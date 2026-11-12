package com.example.policy.controller;

import com.example.policy.dto.OrgCreateRequest;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.llm.IngestService;
import com.example.policy.llm.GrokClient;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final IngestService ingestService;
    private final GrokClient grokClient;

    public AgentController(IngestService ingestService, GrokClient grokClient) {
        this.ingestService = ingestService;
        this.grokClient = grokClient;
    }

    // 触发智能体深度检索（RAG + LLM），严格按范围/数量，由 Prompt 驱动，无爬虫
    @PostMapping("/crawl")
    public Map<String,Object> crawl(@RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                    @RequestParam(value="limit", defaultValue = "20") int limit,
                                    @RequestParam(value = "orgIds", required = false) List<Long> orgIds) {
        return ingestService.ingest(startDate, endDate, limit, orgIds);
    }

    // 新增：流式返回 LLM 原始结果，便于前端实时显示
    @GetMapping(value = "/probe/stream", produces = "text/event-stream;charset=UTF-8")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter probeStreaming(
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value="limit", defaultValue = "20") int limit,
            @RequestParam(value = "orgIds", required = false) List<Long> orgIds,
            @RequestParam(value="maxTokens", defaultValue = "160000") int maxTokens) {

        String resolvedPrompt = prompt;
        if (resolvedPrompt == null || resolvedPrompt.isBlank()) {
            resolvedPrompt = ingestService.buildPrompt(startDate, endDate, limit, orgIds);
        }
        log.info("[SSE] /agent/probe/stream called, resolvedPrompt.length={}, maxTokens={}",
                resolvedPrompt != null ? resolvedPrompt.length() : 0, maxTokens);
        return grokClient.invokeStreaming(resolvedPrompt, maxTokens,orgIds);
    }

    // 新增：流式入库接口 - 合并流式响应和自动入库
    @GetMapping(value = "/probe/stream-ingest", produces = "text/event-stream;charset=UTF-8")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamIngest(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value="limit", defaultValue = "20") int limit,
            @RequestParam(value = "orgIds", required = false) List<Long> orgIds,
            @RequestParam(value="maxTokens", defaultValue = "160000") int maxTokens) {

        log.info("[SSE] /agent/probe/stream-ingest called, startDate={}, endDate={}, limit={}, orgIds={}, maxTokens={}",
                startDate, endDate, limit, orgIds, maxTokens);

        try {
            // 构建提示词
            String prompt = ingestService.buildPrompt(startDate, endDate, limit, orgIds);
            log.info("[SSE] 构建提示词完成，长度: {}", prompt.length());

            // 调用修改后的 GrokClient 流式入库方法
            return grokClient.invokeStreaming(prompt, maxTokens, orgIds);

        } catch (Exception e) {
            log.error("[SSE] 流式入库接口异常: {}", e.getMessage(), e);
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                    new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
            emitter.completeWithError(e);
            return emitter;
        }
    }

    // 新增：提供前端可获取的提示词模板，避免直接访问 /prompts 路径 404
    @GetMapping(value = "/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> promptTemplate(){
        try{
            ClassPathResource res = new ClassPathResource("prompts/ingest_prompt_zh.txt");
            if(!res.exists()) return ResponseEntity.ok("");
            try(InputStream is = res.getInputStream()){
                byte[] bytes = is.readAllBytes();
                return ResponseEntity.ok(new String(bytes, StandardCharsets.UTF_8));
            }
        }catch(Exception e){
            return ResponseEntity.ok("");
        }
    }

    @GetMapping(value = "/prompt/runtime", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> runtimePrompt(@RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                @RequestParam(value = "limit", defaultValue = "20") int limit,
                                                @RequestParam(value = "orgIds", required = false) List<Long> orgIds) {
        String prompt = ingestService.buildPrompt(startDate, endDate, limit, orgIds);
        return ResponseEntity.ok(prompt);
    }

    @PostMapping(value = "/orgs", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createOrg(@RequestBody @Valid OrgCreateRequest request) {
        try {
            PolicyOrg created = ingestService.createOrg(request.getOrgName(), request.getSampleUrl());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/orgs")
    public List<PolicyOrg> listOrgs() {
        return ingestService.listAllOrgs();
    }

    // 新增：诊断接口，用于检查服务状态
    @GetMapping("/diagnose")
    public ResponseEntity<Map<String, Object>> diagnose() {
        try {
            grokClient.diagnose();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "message", "Agent service is running",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", "Diagnosis failed: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }
}