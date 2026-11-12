package com.example.policy.llm;

import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.entity.User;
import com.example.policy.entity.UserFavorite;
import com.example.policy.repository.FavoriteRepository;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.PolicyOrgRepository;
import com.example.policy.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class RagContextBuilder {

    private final PolicyDataRepository policyDataRepository;
    private final PolicyOrgRepository policyOrgRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository; // 新增：用于获取当前用户
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagContextBuilder(PolicyDataRepository policyDataRepository,
                             PolicyOrgRepository policyOrgRepository,
                             FavoriteRepository favoriteRepository,
                             UserRepository userRepository) { // 新增参数
        this.policyDataRepository = policyDataRepository;
        this.policyOrgRepository = policyOrgRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
    }

    public String buildContext(LocalDate start, LocalDate end, int limit) {
        return buildContext(start, end, limit, null);
    }

    public String buildContext(LocalDate start, LocalDate end, int limit, List<Long> orgIds) {
        Long userId = getCurrentUserId();

        final java.util.Set<Long> allowedOrgIds = (orgIds != null && !orgIds.isEmpty()) ?
                new java.util.LinkedHashSet<>(orgIds) : null;

        // 优先从收藏中获取示例数据
        List<PolicyData> samples = getSamplesFromFavorites(userId, allowedOrgIds);

        if (samples.size() < 5) {
            List<PolicyData> randomSamples = getRandomSamples(allowedOrgIds, 5 - samples.size());
            for (PolicyData randomSample : randomSamples) {
                if (samples.size() >= 5) break;
                if (randomSample != null && !containsPolicyData(samples, randomSample)) {
                    samples.add(randomSample);
                }
            }
        }

        if (samples.size() > 5) {
            samples = samples.subList(0, 5);
        }

        Map<String, Object> sampleNode = samples.isEmpty() ? new LinkedHashMap<>() : buildSampleNode(samples.get(0));
        String sampleJson = toPrettyJson(sampleNode);

        // 从收藏中优先获取
        java.util.LinkedHashSet<String> titleExamples = samples.stream()
                .map(p -> safeText(p.getZhTitle()))
                .filter(title -> title != null && !title.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        if (titleExamples.size() < 5 && userId != null) {
            List<UserFavorite> additionalFavorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, org.springframework.data.domain.PageRequest.of(0, 10));

            for (UserFavorite favorite : additionalFavorites) {
                if (titleExamples.size() >= 5) break;
                if (favorite == null || favorite.getPolicyData() == null) continue;

                PolicyData policyData = favorite.getPolicyData();
                // 机构过滤
                if (allowedOrgIds != null && !allowedOrgIds.isEmpty()) {
                    if (policyData.getOrg() == null || !allowedOrgIds.contains(policyData.getOrg().getId())) {
                        continue;
                    }
                }

                String candidateTitle = safeText(policyData.getZhTitle());
                if (candidateTitle != null && !candidateTitle.isBlank()) {
                    titleExamples.add(candidateTitle);
                }
            }
        }


        if (titleExamples.size() < 5) {
            List<PolicyData> additional = policyDataRepository.findRandom(
                    org.springframework.data.domain.PageRequest.of(0, 10));
            for (PolicyData extra : additional) {
                if (titleExamples.size() >= 5) break;
                if (extra == null) continue;
                if (allowedOrgIds != null && !allowedOrgIds.isEmpty()) {
                    if (extra.getOrg() == null || extra.getOrg().getId() == null || !allowedOrgIds.contains(extra.getOrg().getId())) {
                        continue;
                    }
                }
                String candidateTitle = safeText(extra.getZhTitle());
                if (candidateTitle != null && !candidateTitle.isBlank()) {
                    titleExamples.add(candidateTitle);
                }
            }
        }

        List<String> titleList = new java.util.ArrayList<>(titleExamples);
        if (titleList.size() > 5) {
            titleList = titleList.subList(0, 5);
        }
        String titleJson = toPrettyJson(titleList);

        List<PolicyOrg> orgs;
        if (orgIds != null && !orgIds.isEmpty()) {
            List<PolicyOrg> fetched = policyOrgRepository.findAllById(orgIds);
            java.util.Map<Long, PolicyOrg> byId = new java.util.LinkedHashMap<>();
            for (PolicyOrg o : fetched) {
                if (o != null && o.getId() != null) {
                    byId.put(o.getId(), o);
                }
            }
            orgs = orgIds.stream()
                    .map(byId::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (orgs.isEmpty()) {
                orgs = policyOrgRepository.findAll();
            }
        } else {
            orgs = policyOrgRepository.findAll();
        }
        List<Map<String, Object>> orgPayload = orgs.stream().map(o -> {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", o.getId());
            node.put("name", safeText(o.getOrgName()));
            node.put("sample_url", safeUrl(o.getSampleUrl()));
            return node;
        }).collect(Collectors.toList());
        String orgJson = toPrettyJson(orgPayload);

        String timeRange = (start!=null?start:"") + " to " + (end!=null?end:"");
        int maxCount = Math.max(1, limit);
        int minCount = Math.max(1, maxCount - 5);
        String quantityRange = minCount + " to " + maxCount;

        // 1) 若存在外部模板（用户原始提示词），优先加载并做占位符替换
        String external = loadTemplateSafely("prompts/ingest_prompt_zh.txt");
        if (external != null && !external.isBlank()) {
            return external
                    .replace("{{ORG_LIST}}", orgJson)
                    .replace("{{SAMPLE_OBJECT}}", sampleJson)
                    .replace("{{TITLE_LIST}}", titleJson)
                    .replace("{{TIME_RANGE}}", timeRange)
                    .replace("{{QUANTITY_RANGE}}", quantityRange)
                    .replace("{{MIN_REQUIRED}}", String.valueOf(minCount))
                    .replace("{{MAX_REQUIRED}}", String.valueOf(maxCount));
        }

        return "# 角色与任务定义\n\n" +
                "你是一名严格、可靠、零幻觉的数据提取与研判代理。你的核心任务：在限定机构与时间窗口内，检索并输出真实存在、可验证、且具官方 PDF 直链的政策/战略类文件，聚焦国家/地区宏观战略、产业政策、资本与资金流向、长期路线图等高价值内容。任何不确定或不可验证的内容一律剔除。\n\n" +
                "# 输入数据\n\n" +
                "## 1) 目标机构列表 (Target Organization List)\n" +
                orgJson + "\n\n" +
                "## 2) 数据格式示例 (Data Format Example)\n" +
                sampleJson + "\n\n" +
                "## 3) 方向参考标题 (Directional Title Examples)\n" +
                titleJson + "\n\n" +
                "## 4) 任务参数 (Task Parameters)\n" +
                "- 时间范围 (Time Range, inclusive): {" + timeRange + "}\n" +
                "- 数量要求 (Target Count): 至少 {" + minCount + "} 条，至多 {" + maxCount + "} 条；首选接近上限。\n\n" +
                "# 严格约束（CRITICAL, MUST COMPLY）\n" +
                "1. 时间严格性：仅返回 publication_date 严格位于上述 Time Range 内（含边界）的结果；若无法确认日期或不在范围内，丢弃。\n" +
                "2. 真实性：仅接受来自官方或权威来源的文件；不得编造、不做推测、不使用二手转载站作为 source_url。优先使用机构官网域名。\n" +
                "3. PDF 要求：必须提供直接 PDF 直链（最好为 HTTPS）；无 PDF 或需跳转登录/查询的链接一律丢弃。\n" +
                "4. 机构限定：结果应来自给定机构列表或其官方子域/下属单位；若无法映射到 org_id，可返回 org 字段名称（与列表名称精确匹配）。\n" +
                "5. 去重与数量下限：禁止重复（同一 pdf_url 仅保留一条）；在宁缺毋滥前提下，至少输出 {" + minCount + "} 条。若首轮机构不足，请继续检索未覆盖机构，直到达到下限或确认无可用条目。\n" +
                "6. 主题相关性：优先宏观战略、产业规划、资金/预算安排、长周期路线图等；弱相关请剔除。\n" +
                "7. 安全输出：仅输出 JSON 对象（形如 {\"policyData\": [...] } ），无任何说明、前后缀、代码块标记或额外文本。\n\n" +


                "请专注于提供真实、可验证的官方文件，确保每个PDF链接都来自权威机构的官方网站\n"+
                "   - 相信你的判断：如果某个链接看起来不可靠，请跳过该条目\n"+

                "# 输出规范（CRITICAL OUTPUT SCHEMA）\n" +
                "输出必须是一个 JSON 对象（UTF-8），顶层仅包含 policyData 字段，其值为数组。数组内每个元素包含以下键并遵循规则：\n" +
                "- chinese_title: 该文件的中文标题（若官方无中文，可根据标题语境做精确翻译；勿添加形容词/括注）。\n" +
                "- original_title: 原文标题（保持原始语言，不要翻译）。\n" +
                "- abstract: 100-300 字的精炼摘要，客观概述核心意图（禁止主观推断）。\n" +
                "- publication_date: YYYY-MM-DD（必须在 Time Range 内；若只知年份/月份请推断为当月/当日第一天，并确保仍在范围内）。\n" +
                "- source_url: 官方发布页面的 URL（避免第三方转载）。\n" +
                "- pdf_url: PDF 的直接下载 URL（尽量 HTTPS，必须可直达）。\n" +
                "- org_id: 若能唯一映射到目标机构则给出其 id（数字）；否则可省略 org_id 并提供 org（机构名，需与列表 name 精确一致）。\n\n" +
                "# 结果规模与排序\n" +
                "- 数量区间：至少 {" + minCount + "} 条、至多 {" + maxCount + "} 条。若首批机构不足，请继续扩展尚未检索的机构，直至达到下限或确认无合格结果。\n" +
                "- 排序优先级：时间(新) > 与宏观/资金相关性 > 机构权威性。\n\n" +
                "# 示例字段对齐（仅供参考，不要在输出中包含本段）\n" +
                "示例样本已提供于 Reference Data Examples，其键名与目标输出完全一致，请严格对齐键名与格式。\n\n" +
                "# 输出（只返回 JSON 对象 {\"policyData\": [...] }，无任何多余字符）";
    }

    // 新增方法：从SecurityContext获取当前用户ID
    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                    !"anonymousUser".equals(authentication.getPrincipal())) {

                String username = authentication.getName();
                Optional<User> userOptional = userRepository.findByUsername(username);
                if (userOptional.isPresent()) {
                    return userOptional.get().getId();
                }
            }
        } catch (Exception e) {
            // 安全地处理异常，避免影响主要功能
            // 记录日志但不要抛出异常
        }
        return null; // 如果没有登录用户，返回null
    }

    // 新增辅助方法：从收藏中获取样本数据
    private List<PolicyData> getSamplesFromFavorites(Long userId, java.util.Set<Long> allowedOrgIds) {
        List<PolicyData> favoriteSamples = new java.util.ArrayList<>();

        if (userId != null) {
            List<UserFavorite> favorites;
            if (allowedOrgIds != null && !allowedOrgIds.isEmpty()) {
                // 有机构过滤的收藏查询
                favorites = favoriteRepository.findByUserIdAndPolicyDataOrgIdIn(userId, new java.util.ArrayList<>(allowedOrgIds));
            } else {
                // 无机构过滤的收藏查询
                favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId,
                        org.springframework.data.domain.PageRequest.of(0, 5));
            }

            // 提取PolicyData对象
            favoriteSamples = favorites.stream()
                    .map(UserFavorite::getPolicyData)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return favoriteSamples;
    }

    // 新增辅助方法：获取随机样本（回退逻辑）
    private List<PolicyData> getRandomSamples(java.util.Set<Long> allowedOrgIds, int count) {
        if (count <= 0) return new java.util.ArrayList<>();

        List<PolicyData> randomSamples = policyDataRepository.findRandom(
                org.springframework.data.domain.PageRequest.of(0, count * 2)); // 多查一些用于过滤

        if (allowedOrgIds != null && !allowedOrgIds.isEmpty()) {
            randomSamples = randomSamples.stream()
                    .filter(p -> p.getOrg() != null && allowedOrgIds.contains(p.getOrg().getId()))
                    .limit(count)
                    .collect(Collectors.toList());
        } else {
            if (randomSamples.size() > count) {
                randomSamples = randomSamples.subList(0, count);
            }
        }

        return randomSamples;
    }

    // 新增辅助方法：检查是否包含相同的PolicyData
    private boolean containsPolicyData(List<PolicyData> list, PolicyData item) {
        if (item == null || item.getId() == null) return false;
        return list.stream().anyMatch(p -> p.getId() != null && p.getId().equals(item.getId()));
    }

    private String safeText(String value) {
        return normalizeText(value);
    }

    private String safeSummary(String value, int max) {
        String cleaned = normalizeText(value);
        if (cleaned.length() > max) {
            cleaned = cleaned.substring(0, max);
        }
        return cleaned;
    }

    private Map<String, Object> buildSampleNode(PolicyData p) {
        Map<String, Object> node = new LinkedHashMap<>();
        if (p == null) {
            return node;
        }
        node.put("chinese_title", safeText(p.getZhTitle()));
        node.put("original_title", safeText(p.getOriginalTitle()));
        node.put("abstract", safeSummary(p.getSummary(), 120));
        node.put("publication_date", p.getPublishDate() != null ? p.getPublishDate().toString() : "");
        node.put("source_url", safeUrl(p.getSourceUrl()));
        node.put("pdf_url", safeUrl(p.getPdfUrl()));
        if (p.getOrg() != null && p.getOrg().getId() != null) {
            node.put("org_id", p.getOrg().getId());
        } else {
            node.put("org_id", null);
        }
        return node;
    }

    private String safeUrl(String value) {
        return normalizeUrl(value);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\u00A0\\u1680\\u2000-\\u200A\\u200B\\u202F\\u205F\\u3000]", " ");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ");
        cleaned = cleaned.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        cleaned = cleaned.trim().replaceAll(" +", " ");
        return cleaned;
    }

    private String normalizeUrl(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
                .replaceAll("[\\u00A0\\u1680\\u2000-\\u200A\\u200B\\u202F\\u205F\\u3000]", " ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replaceAll("\\s+", "");
        return cleaned.trim();
    }

    private String toPrettyJson(Object node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String loadTemplateSafely(String path) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (!res.exists()) return null;
            try (InputStream is = res.getInputStream()) {
                byte[] bytes = is.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }
}