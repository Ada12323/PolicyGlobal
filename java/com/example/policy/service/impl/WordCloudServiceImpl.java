package com.example.policy.service.impl;

import com.example.policy.entity.PolicyData;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.service.WordCloudService;
import org.springframework.stereotype.Service;
import com.hankcs.hanlp.HanLP;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WordCloudServiceImpl implements WordCloudService {

    private final PolicyDataRepository policyDataRepository;
    // 补充少量通用停用词（HanLP 自带停用词库基础上再过滤一层）
    private static final Set<String> EXTRA_STOP = new HashSet<>(Arrays.asList(
        "的","了","和","与","及","等","在","对","为","是","以","及其","根据",
        "通过","并","并且","其中","关于","本","该","各","按","按照","向","至","对于",
        "或者","或","被","将","及其","以及","进一步","推进","开展","加强","促进",
        // 常见空洞动词/名词，易污染词云
        "提出","基础","相关","情况","工作","问题","落实","完善","明确","规定","要求","实施",
        "组织","部门","依据","适用","办法","意见","方案","报告","通知","信息","单位","人员",
        "产品","服务","企业","机构","事项","内容","地区","区域","行业","领域","方面","本市","本省"
    ));

    public WordCloudServiceImpl(PolicyDataRepository policyDataRepository) {
        this.policyDataRepository = policyDataRepository;
    }

    @Override
    public List<Map<String, Object>> topWords(int limit) {
        List<PolicyData> all = policyDataRepository.findAll();
        Map<String, Integer> freq = new HashMap<>();
        for (PolicyData p : all) {
            String text = (p.getZhTitle() == null ? "" : p.getZhTitle()) + "\n" + (p.getSummary() == null ? "" : p.getSummary());
            if (text.isBlank()) continue;
            // 控制单文档长度，避免极长文本影响性能
            if (text.length() > 6000) text = text.substring(0, 6000);
            // 以“词”为主：优先使用 TF-IDF 关键词（HanLP），最后才退化
            List<String> kws;
            try {
                kws = HanLP.extractKeyword(text, 25); // 直接提取关键词（词为主）
            } catch (Throwable e) {
                // 异常时退化为简单切词：按非字母数字汉字分隔
                kws = Arrays.stream(text.split("[^A-Za-z0-9\u4e00-\u9fa5]+"))
                        .filter(s -> s != null && s.length() >= 2)
                        .limit(50)
                        .collect(Collectors.toList());
            }
            for (String w : kws) {
                if (w == null) continue;
                String ww = w.trim();
                if (ww.length() < 2) continue;
                // 过滤纯数字/标点
                if (ww.matches("^[\\p{Punct}0-9]+$")) continue;
                if (EXTRA_STOP.contains(ww)) continue;
                // 过滤常见后缀噪声（如“工作/情况/机制/问题/要求/落实”等作为尾缀时）
                String cleaned = ww.replaceAll("(工作|情况|问题|机制|体系|要求|意见|方案|规定|通知)$", "");
                if (cleaned.length() < 2) continue;
                freq.put(cleaned, freq.getOrDefault(cleaned, 0) + 1);
            }
        }
        return freq.entrySet().stream()
                .sorted((a,b) -> b.getValue() - a.getValue())
                .limit(limit)
                .map(e -> {
                    Map<String,Object> m = new HashMap<>();
                    m.put("word", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList());
    }
}
