package com.example.policy.llm;

import com.example.policy.assistant.client.QwenClient;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.entity.UserPolicyRelation;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.PolicyOrgRepository;
import com.example.policy.repository.UserPolicyRelationRepository;
import com.example.policy.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final GrokClient grokClient;
    private final RagContextBuilder ragContextBuilder;
    private final QwenClient qwenClient;
    private final PolicyDataRepository policyDataRepository;
    private final PolicyOrgRepository policyOrgRepository;
    private final UserPolicyRelationRepository userPolicyRelationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IngestService(GrokClient grokClient, RagContextBuilder ragContextBuilder, PolicyDataRepository policyDataRepository, PolicyOrgRepository policyOrgRepository, QwenClient qwenClient, UserPolicyRelationRepository userPolicyRelationRepository, UserRepository userRepository) {
        this.grokClient = grokClient;
        this.ragContextBuilder = ragContextBuilder;
        this.policyDataRepository = policyDataRepository;
        this.policyOrgRepository = policyOrgRepository;
        this.qwenClient = qwenClient;
        this.userPolicyRelationRepository = userPolicyRelationRepository;
        this.userRepository = userRepository;

        // 设置GrokClient的IngestService引用
        this.grokClient.setIngestService(this);
    }

    // 工具方法：获取当前登录用户ID
    private Long getCurrentLoginUserId() {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        log.info("getCurrentLoginUserId: uid={}, username={}", uid, username);
        return uid;
    }

    @Transactional(readOnly = true)
    public String buildPrompt(LocalDate start, LocalDate end, int limit) {
        return buildPrompt(start, end, limit, null);
    }

    @Transactional(readOnly = true)
    public String buildPrompt(LocalDate start, LocalDate end, int limit, List<Long> orgIds) {
        return ragContextBuilder.buildContext(start, end, limit, orgIds);
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String,Object> ingest(LocalDate start, LocalDate end, int limit, List<Long> orgIds) {
        String prompt = buildPrompt(start, end, limit, orgIds);
        // 传入 <=0 让 GrokClient 使用配置的默认 max-tokens（application.yml -> llm.grok.max-tokens）
        List<Map<String,Object>> raw = null;
        String llmRaw = null;
        try {
            // 兼容新版GrokClient.invoke返回原始字符串（如需）
            Object result = grokClient.invoke(prompt, -1);
            if (result instanceof List) {
                raw = (List<Map<String,Object>>) result;
            } else if (result instanceof String) {
                llmRaw = (String) result;
            }
        } catch (Exception e) {
            llmRaw = null;
        }
        if (raw != null && raw.size() == 1 && raw.get(0) != null && raw.get(0).containsKey("rawText")) {
            llmRaw = string(raw.get(0).get("rawText"));
            raw = null;
        }
        // 若拿到原始字符串，尝试提取【可入库JSON】段
        if (raw == null && llmRaw != null) {
            raw = tryParseJsonArray(llmRaw);
            if (raw == null) {
                String json = extractJsonBlock(llmRaw, "【可入库JSON】", "【可入库JSON结束】");
                if (json != null) {
                    raw = tryParseJsonArray(json);
                }
            }
        }

        // 第二阶段：将 Grok 的输出交给 Qwen 进行严格重格式化，仅输出 JSON 数组（无多余文本）
        try {
            String sourcePayload;
            if (raw != null) {
                sourcePayload = objectMapper.writeValueAsString(raw);
            } else {
                sourcePayload = llmRaw;
            }
            if (sourcePayload != null && !sourcePayload.isBlank()) {
                List<Map<String,Object>> reformatted = reformatWithQwen(sourcePayload);
                if (reformatted != null && !reformatted.isEmpty()) {
                    raw = reformatted;
                }
            }
        } catch (Exception qex) {
            log.warn("Qwen reformat step skipped due to error: {}", qex.toString());
        }
        int total = 0, inserted = 0, skippedNoPdf = 0, duplicate = 0;
        if (raw != null) {
            for (Map<String,Object> item : raw) {
                total++;
                String pdf = string(item.get("pdf_url"));
                if (pdf == null || pdf.isBlank() || !pdf.toLowerCase().endsWith(".pdf")) { skippedNoPdf++; continue; }
                String source = string(item.get("source_url"));
                // 规范化去重：忽略大小写与首尾空格
                if (policyDataRepository.existsByPdfUrlNormalized(pdf)) { duplicate++; continue; }
                // 兜底（历史数据或索引差异）
                if (policyDataRepository.existsByPdfUrl(pdf)) { duplicate++; continue; }
                PolicyData p = new PolicyData();
                p.setZhTitle(limitLen(coalesce(item, "chinese_title","zh_title"),500));
                p.setOriginalTitle(limitLen(string(item.get("original_title")),500));
                p.setSummary(limitLen(coalesce(item, "abstract","summary"),4000));
                p.setPublishDate(parseDate(coalesce(item, "publication_date","publish_date")));
                p.setSourceUrl(limitLen(source,1000));
                p.setPdfUrl(limitLen(pdf,1000));
                // org 关联：优先使用 org_id；否则回退 org 名称匹配
                Long orgId = parseLong(string(item.get("org_id")));
                if (orgId != null) {
                    policyOrgRepository.findById(orgId).ifPresent(p::setOrg);
                } else if (item.get("org") != null) {
                    String orgName = string(item.get("org"));
                    if (orgName != null) policyOrgRepository.findByOrgName(orgName).ifPresent(p::setOrg);
                }
                policyDataRepository.save(p);
                inserted++;
            }
        }
        Map<String,Object> res = new HashMap<>();
        res.put("llmRaw", llmRaw);
        res.put("total", total);
        res.put("inserted", inserted);
        res.put("skippedNoPdf", skippedNoPdf);
        res.put("duplicate", duplicate);
        return res;
    }

    /**
     * 新增方法：从流式调用结果直接入库
     */
    // 在 IngestService.java 中修改入库方法
    @Transactional
    public Map<String, Object> directIngestFromStreaming(String streamingResponse, List<Long> orgIds) {
        log.info("=== IngestService.directIngestFromStreaming 方法开始 ===");
        log.info("流式响应长度: {}, orgIds: {}", streamingResponse.length(), orgIds);

        Map<String, Object> result = new HashMap<>();
        int total = 0, inserted = 0, skippedNoPdf = 0, duplicate = 0, error = 0,existingAssociate = 0;
        Long currentUserId = getCurrentLoginUserId();
        List<Long> associatedPolicyIds = new ArrayList<>();

        try {
            // 尝试解析流式响应
            List<Map<String, Object>> rawData = tryParseJsonArray(streamingResponse);

            if (rawData == null) {
                // 如果直接解析失败，尝试提取JSON块
                String jsonBlock = extractJsonBlock(streamingResponse, "【可入库JSON】", "【可入库JSON结束】");
                if (jsonBlock != null) {
                    rawData = tryParseJsonArray(jsonBlock);
                }
            }

            if (rawData != null && !rawData.isEmpty()) {
                log.info(" 解析出 {} 条数据准备入库", rawData.size());

                for (Map<String, Object> item : rawData) {
                    total++;
                    try {
                        String pdf = string(item.get("pdf_url"));
                        if (pdf == null || pdf.isBlank() || !pdf.toLowerCase().endsWith(".pdf")) {
                            skippedNoPdf++;
                            continue;
                        }

                        // 规范化去重
                        if (policyDataRepository.existsByPdfUrlNormalized(pdf) ||
                                policyDataRepository.existsByPdfUrl(pdf)) {
                            duplicate++;
                            continue;
                        }
                        // 2. 核心：先查总政策表是否存在该PDF的政策
                        PolicyData policy = policyDataRepository.findByPdfUrl(pdf).orElse(null);
                        log.info(" 解析出 PDF: {}, 政策: {}", pdf, policy);
                        if (policy == null) {
                            // 3. 不存在则新建入库
                            policy = new PolicyData();
                            policy.setZhTitle(limitLen(coalesce(item, "chinese_title", "zh_title"), 500));
                            policy.setOriginalTitle(limitLen(string(item.get("original_title")), 500));
                            policy.setSummary(limitLen(coalesce(item, "abstract", "summary"), 4000));
                            policy.setPublishDate(parseDate(coalesce(item, "publication_date", "publish_date")));
                            policy.setSourceUrl(limitLen(string(item.get("source_url")), 1000));
                            policy.setPdfUrl(limitLen(pdf, 1000));
                            // 关联机构（复用原有逻辑）
                            Long orgId = parseLong(string(item.get("org_id")));
                            if (orgId != null && orgIds.contains(orgId)) {
                                policyOrgRepository.findById(orgId).ifPresent(policy::setOrg);
                            } else if (!orgIds.isEmpty()) {
                                policyOrgRepository.findById(orgIds.get(0)).ifPresent(policy::setOrg);
                            }
                            policy = policyDataRepository.save(policy);
                            log.info(" 入库成功,新增政策: {}", policy);
                            log.info("用户Id: {}",currentUserId);
                            inserted++;
                        }

                        // 4. 无论政策是否新增，都检查关联表：当前用户是否已关联该政策
                        if (currentUserId != null) {
                            // 先打印当前认证信息，排查是否为匿名用户
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            log.info("当前认证用户：{}，是否匿名：{}", auth.getName(), auth instanceof AnonymousAuthenticationToken);

                            // 仅非匿名用户执行关联表操作
                            if (!userPolicyRelationRepository.existsByUserIdAndPolicyId(currentUserId, policy.getId())) {
                                UserPolicyRelation relation = UserPolicyRelation.builder()
                                        .userId(currentUserId)
                                        .policyId(policy.getId())
                                        .build();
                                log.info(" 关联用户: {}, 政策: {}", currentUserId, policy);
                                userPolicyRelationRepository.save(relation);
                                associatedPolicyIds.add(policy.getId());
                                existingAssociate++;
                            }
                        } else {
                            log.warn("当前无有效用户ID，跳过用户-政策关联");
                        }



                    } catch (Exception e) {
                        error++;
                        log.error(" 入库第 {} 条记录失败: {}", total, e.getMessage());
                    }
                }
            } else {
                log.warn(" 流式响应无法解析为有效数据");
                result.put("error", "无法解析LLM响应数据");
            }

            result.put("total", total);
            result.put("inserted", inserted);
            result.put("skippedNoPdf", skippedNoPdf);
            result.put("duplicate", duplicate);
            result.put("errorCount", error);
            result.put("existingAssociate", existingAssociate);
            result.put("associatedPolicyIds", associatedPolicyIds);

        } catch (Exception e) {
            log.error(" 流式调用结果入库失败: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
        }

        log.info("=== IngestService.directIngestFromStreaming 方法结束 ===");
        return result;
    }

    @Transactional
    public PolicyOrg createOrg(String orgName, String sampleUrl) {
        if (!StringUtils.hasText(orgName)) {
            throw new IllegalArgumentException("机构名称不能为空");
        }
        String normalizedName = orgName.trim();
        policyOrgRepository.findByOrgNameIgnoreCase(normalizedName)
                .ifPresent(existing -> { throw new IllegalArgumentException("机构名称已存在"); });
        String normalizedUrl = (sampleUrl != null && !sampleUrl.trim().isEmpty()) ? sampleUrl.trim() : null;
        PolicyOrg org = PolicyOrg.builder()
                .orgName(normalizedName)
                .sampleUrl(normalizedUrl)
                .build();
        PolicyOrg saved = policyOrgRepository.save(org);
        log.info("Created new PolicyOrg id={}, name={}", saved.getId(), saved.getOrgName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PolicyOrg> listAllOrgs() {
        return policyOrgRepository.findAll();
    }

    // 提取指定分隔符之间的内容
    private String extractJsonBlock(String text, String start, String end) {
        if (text == null) return null;
        int i = text.indexOf(start);
        int j = text.indexOf(end, i+start.length());
        if (i >= 0 && j > i) {
            return text.substring(i+start.length(), j).trim();
        }
        return null;
    }

    private List<Map<String,Object>> tryParseJsonArray(String text) {
        if (text == null) return null;
        String candidate = text.trim();
        if (candidate.isEmpty()) return null;

        List<Map<String,Object>> parsed = attemptParse(candidate);
        if (parsed != null) return parsed;

        // 处理代码块包裹 ```json ... ```
        if (candidate.startsWith("```")) {
            String stripped = stripCodeFence(candidate);
            parsed = attemptParse(stripped);
            if (parsed != null) return parsed;
            parsed = extractPolicyDataFromMixedText(stripped);
            if (parsed != null) return parsed;
        }

        parsed = extractPolicyDataFromMixedText(candidate);
        if (parsed != null) return parsed;

        // 处理包含其他注释时提取第一对中括号
        int start = candidate.indexOf('[');
        int end = candidate.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String sliced = candidate.substring(start, end + 1);
            parsed = attemptParse(sliced.trim());
            if (parsed != null) return parsed;
        }

        // 如为对象包装且前后含有其他文本，尝试截取首尾花括号
        int objStart = candidate.indexOf('{');
        int objEnd = candidate.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            String slicedObject = candidate.substring(objStart, objEnd + 1);
            parsed = attemptParse(slicedObject.trim());
            if (parsed != null) return parsed;
        }

        log.warn("Unable to parse JSON array from LLM output preview: {}", candidate.length() > 200 ? candidate.substring(0, 200) + "..." : candidate);
        return null;
    }

    // 通过 Qwen 对 Grok 输出进行"严格重格式化"（已禁用）
    private List<Map<String,Object>> reformatWithQwen(String source) {
        log.info("⏭ Qwen重格式化已禁用");
        return null;
    }

    private List<Map<String,Object>> attemptParse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String,Object>>>(){});
        } catch (Exception ignored) {
            // fallback to object with policyData key
        }
        try {
            Map<String,Object> obj = objectMapper.readValue(json, new TypeReference<Map<String,Object>>(){ });
            List<Map<String,Object>> wrapped = extractPolicyData(obj);
            if (wrapped != null) {
                return wrapped;
            }
        } catch (Exception ignored) {
            // swallow and return null below
        }
        return null;
    }

    private List<Map<String,Object>> extractPolicyData(Map<String,Object> obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }
        for (Map.Entry<String,Object> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            if ("policyData".equals(key) || "policydata".equalsIgnoreCase(key)) {
                return convertPolicyDataNode(entry.getValue());
            }
        }
        return null;
    }

    private String stripCodeFence(String block) {
        if (block == null) return null;
        int firstNewline = block.indexOf('\n');
        int lastFence = block.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return block.substring(firstNewline + 1, lastFence).trim();
        }
        return block;
    }

    private String string(Object o){return o==null?null:String.valueOf(o).trim();}
    private String coalesce(Map<String,Object> m, String a, String b){
        String va = string(m.get(a)); if (va!=null && !va.isBlank()) return va; return string(m.get(b));
    }
    private String limitLen(String v,int max){return v==null?null:(v.length()>max?v.substring(0,max):v);}
    private LocalDate parseDate(String v){
        try {return (v==null||v.isBlank())?null:LocalDate.parse(v);}catch(Exception e){return null;}
    }
    private Long parseLong(String v){try{return (v==null||v.isBlank())?null:Long.parseLong(v);}catch(Exception e){return null;}}

    private List<Map<String,Object>> extractPolicyDataFromMixedText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        final int length = text.length();
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            if (ch != '{' && ch != '[') {
                continue;
            }
            String fragment = text.substring(i);
            try {
                JSONTokener tokener = new JSONTokener(fragment);
                Object parsed = tokener.nextValue();
                List<Map<String,Object>> candidate = resolvePolicyData(parsed);
                if (candidate != null) {
                    return candidate;
                }
            } catch (JSONException ex) {
                // 忽略不合法的 JSON 片段，继续扫描
            }
        }
        return null;
    }

    private List<Map<String,Object>> resolvePolicyData(Object node) {
        if (node == null) {
            return null;
        }
        if (node instanceof JSONObject jsonObject) {
            if (jsonObject.has("policyData")) {
                Object policyNode = jsonObject.opt("policyData");
                List<Map<String,Object>> converted = convertPolicyDataNode(policyNode);
                if (converted != null) {
                    return converted;
                }
            }
            for (String key : jsonObject.keySet()) {
                Object nested = jsonObject.opt(key);
                List<Map<String,Object>> candidate = resolvePolicyData(nested);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (node instanceof JSONArray jsonArray) {
            for (int idx = 0; idx < jsonArray.length(); idx++) {
                List<Map<String,Object>> candidate = resolvePolicyData(jsonArray.opt(idx));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private List<Map<String,Object>> convertPolicyDataNode(Object node) {
        if (node == null) {
            return Collections.emptyList();
        }
        try {
            if (node instanceof JSONArray array) {
                return objectMapper.readValue(array.toString(), new TypeReference<List<Map<String,Object>>>(){});
            }
            if (node instanceof JSONObject single) {
                JSONArray wrapper = new JSONArray();
                wrapper.put(single);
                return objectMapper.readValue(wrapper.toString(), new TypeReference<List<Map<String,Object>>>(){});
            }
            return objectMapper.convertValue(node, new TypeReference<List<Map<String,Object>>>(){});
        } catch (Exception ex) {
            log.warn("Unable to convert policyData node: {}", ex.getMessage());
            return null;
        }
    }
}
