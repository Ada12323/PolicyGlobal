package com.example.policy.controller;

import com.example.policy.entity.PolicyData;
import com.example.policy.service.PolicyDataService;
import com.example.policy.service.UserPolicyRelationService;
import com.example.policy.service.impl.UserPolicyRelationServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.policy.llm.IngestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.policy.dto.PageResponse;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {

    private final PolicyDataService policyDataService;
    private final IngestService ingestService;
    private final UserPolicyRelationService userPolicyRelationService;

    public PolicyController(PolicyDataService policyDataService, IngestService ingestService, UserPolicyRelationServiceImpl userPolicyRelationServiceImpl, UserPolicyRelationService userPolicyRelationService) {
        this.policyDataService = policyDataService;
        this.ingestService = ingestService;
        this.userPolicyRelationService = userPolicyRelationService;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(name = "keyword", required = false) String keyword,
                                            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(name = "limit", defaultValue = "20") int limit,
                                            Authentication authentication) {
        // 取消未登录脱敏，直接返回完整标题与摘要
        return policyDataService.search(keyword, startDate, endDate, limit).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/detail/{id}")
    public Map<String, Object> detail(@PathVariable("id") Long id) {
        PolicyData p = policyDataService.getDetail(id, true);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");
        return toDto(p);
    }

    @GetMapping("/org/stats")
    public List<Map<String, Object>> orgStats() {
        return policyDataService.aggregateByOrg().stream().map(arr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orgName", arr[0]);
            m.put("count", arr[1]);
            return m;
        }).collect(Collectors.toList());
    }

    @GetMapping("/home")
    public Map<String, Object> home(Authentication authentication) {
        boolean loggedIn = authentication != null && authentication.isAuthenticated();
        String username = (authentication != null && authentication.isAuthenticated()) ? authentication.getName() : null;
        List<PolicyData> latest = policyDataService.latest(10);
        List<PolicyData> hot = policyDataService.hot(10);
        List<PolicyData> recommend = loggedIn ? policyDataService.recommendForUser(username, 10) : List.of();
        Map<String, Object> result = new HashMap<>();
        result.put("latest", latest.stream().map(this::toDto).collect(Collectors.toList()));
        result.put("hot", hot.stream().map(this::toDto).collect(Collectors.toList()));
        result.put("recommend", recommend.stream().map(this::toDto).collect(Collectors.toList()));
        return result;
    }

    @GetMapping("/page/{type}")
    public PageResponse<Map<String,Object>> page(@PathVariable("type") String type,
                                                 @RequestParam(name="page", defaultValue = "0") int page,
                                                 @RequestParam(name="size", defaultValue = "10") int size,
                                                 Authentication authentication){
        int p = Math.max(0, page);
        int s = switch(size){ case 15 -> 15; case 20 -> 20; default -> 10; };
        switch (type) {
            case "latest" -> {
                var pg = policyDataService.latestPage(p, s);
                var items = pg.getContent().stream().map(this::toDto).collect(Collectors.toList());
                return new PageResponse<>(items, pg.getTotalElements(), p, s);
            }
            case "hot" -> {
                var pg = policyDataService.hotPage(p, s);
                var items = pg.getContent().stream().map(this::toDto).collect(Collectors.toList());
                return new PageResponse<>(items, pg.getTotalElements(), p, s);
            }
            case "recommend" -> {
                String username = (authentication != null && authentication.isAuthenticated()) ? authentication.getName() : null;
                var pg = policyDataService.recommendPage(username, p, s);
                var items = pg.getContent().stream().map(this::toDto).collect(Collectors.toList());
                return new PageResponse<>(items, pg.getTotalElements(), p, s);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown type");
        }
    }

    @GetMapping("/export/csv")
    public void exportCsv(@RequestParam(name = "keyword", required = false) String keyword,
                          @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                          @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                          @RequestParam(name = "limit", defaultValue = "100") int limit,
                          HttpServletResponse response) throws Exception {
        List<PolicyData> list = policyDataService.search(keyword, startDate, endDate, limit);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=policy_export.csv");
        String header = "id,zh_title,original_title,summary,publish_date,source_url,pdf_url,org_name,clicks\n";
        response.getWriter().write(header);
        for (PolicyData p : list) {
            String orgName = null;
            try {
                orgName = p.getOrg() != null ? p.getOrg().getOrgName() : null;
            } catch (org.hibernate.LazyInitializationException ex) {
                orgName = null; // 会话外忽略
            }
            String line = String.format("%d,\"%s\",\"%s\",\"%s\",%s,%s,%s,%s,%d\n",
                    p.getId(), escape(p.getZhTitle()), escape(p.getOriginalTitle()), escape(truncate(p.getSummary(), 200)),
                    p.getPublishDate(), safe(p.getSourceUrl()), safe(p.getPdfUrl()), orgName != null ? escape(orgName) : "", p.getClicks());
            response.getWriter().write(line);
        }
        response.getWriter().flush();
    }

    @PostMapping("/ingest/manual")
    public Map<String, Object> manualIngest(@RequestParam(name = "startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(name = "endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ingestService.ingest(startDate, endDate, limit, null);
    }

    private Map<String, Object> toDto(PolicyData p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("zhTitle", p.getZhTitle());
        m.put("originalTitle", p.getOriginalTitle());
        m.put("summary", p.getSummary());
        m.put("publishDate", p.getPublishDate());
        m.put("sourceUrl", p.getSourceUrl());
        m.put("pdfUrl", p.getPdfUrl());
        m.put("clicks", p.getClicks());
        m.put("orgName", p.getOrg() != null ? p.getOrg().getOrgName() : null);
        return m;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> toDtoWithMask(PolicyData p, boolean mask) {
        Map<String, Object> base = toDto(p);
        if (mask) {
            base.put("zhTitle", maskText((String) base.get("zhTitle"), 6));
            base.put("summary", maskText((String) base.get("summary"), 30));
            base.put("pdfUrl", null);
            base.put("sourceUrl", null);
        }
        return base;
    }

    private String maskText(String text, int keep) {
        if (text == null) return null;
        return text.length() <= keep ? text : text.substring(0, keep) + "***";
    }

    @DeleteMapping("/mydetail/{policyId}")
    public ResponseEntity<?> deleteMyDetail(@PathVariable("policyId") Long policyId) {
        userPolicyRelationService.deleteUserPolicyRelation(policyId);
        return ResponseEntity.ok().build();
    }


    private String escape(String v) { return v == null ? "" : v.replace("\"", "' ").replace("\n"," ").replace("\r"," "); }
    private String truncate(String v, int max) { return v == null ? "" : (v.length() > max ? v.substring(0, max) : v); }
    private String safe(String v) { return v == null ? "" : v; }
}
