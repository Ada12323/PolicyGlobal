package com.example.policy.service.impl;

import com.example.policy.entity.PolicyData;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.UserActionLogRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.PolicyDataService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PolicyDataServiceImpl implements PolicyDataService {

    private final PolicyDataRepository policyDataRepository;
    private final UserActionLogRepository userActionLogRepository;
    private final UserRepository userRepository;

    public PolicyDataServiceImpl(PolicyDataRepository policyDataRepository,
                                 UserActionLogRepository userActionLogRepository,
                                 UserRepository userRepository) {
        this.policyDataRepository = policyDataRepository;
        this.userActionLogRepository = userActionLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<PolicyData> search(String keyword, LocalDate start, LocalDate end, int limit) {
        return policyDataRepository.search(keyword == null || keyword.isBlank() ? null : keyword, start, end, PageRequest.of(0, Math.min(limit, 50)));
    }

    @Override
    @Transactional
    public PolicyData getDetail(Long id, boolean increaseClicks) {
    PolicyData p = policyDataRepository.findByIdWithOrg(id).orElse(null);
        if (p != null && increaseClicks) {
            policyDataRepository.incrementClicks(id);
            Long cur = p.getClicks();
            p.setClicks((cur == null ? 0L : cur) + 1);
        }
        return p;
    }

    @Override
    public List<Object[]> aggregateByOrg() {
        return policyDataRepository.aggregateByOrg();
    }

    @Override
    public List<PolicyData> hot(int limit) {
    int size = Math.min(limit, 50);
    List<Long> ids = policyDataRepository.findTopIds(PageRequest.of(0, size));
    if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
    // 保持原有点击量排序
    List<PolicyData> list = policyDataRepository.findByIdInFetch(ids);
    list.sort((a, b) -> {
        long cb = b.getClicks() == null ? 0L : b.getClicks();
        long ca = a.getClicks() == null ? 0L : a.getClicks();
        int c = Long.compare(cb, ca);
        if (c != 0) return c;
        // 次级：发布日期旧→新（ASC），旧的更靠后
        java.time.LocalDate da = a.getPublishDate();
        java.time.LocalDate db = b.getPublishDate();
        if (da != null || db != null) {
            if (da == null) return 1; // null 最后
            if (db == null) return -1;
            int d = da.compareTo(db);
            if (d != 0) return d;
        }
        // 兜底：ID 小→大（小的更靠后）
        long ia = a.getId() == null ? Long.MAX_VALUE : a.getId();
        long ib = b.getId() == null ? Long.MAX_VALUE : b.getId();
        return Long.compare(ia, ib);
    });
    return list;
    }

    @Override
    public List<PolicyData> latest(int limit) {
        int size = Math.min(limit, 50);
        List<Long> ids = policyDataRepository.findLatestIds(PageRequest.of(0, size));
        if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
        List<PolicyData> list = policyDataRepository.findByIdInFetchNoOrder(ids);
        // 按ids顺序恢复
        java.util.Map<Long, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        list.sort(java.util.Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)));
        return list;
    }

    @Override
    public org.springframework.data.domain.Page<PolicyData> latestPage(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        List<Long> ids = policyDataRepository.findLatestIds(PageRequest.of(p, s));
        if (ids == null || ids.isEmpty()) return new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(p, s), 0);
        List<PolicyData> list = policyDataRepository.findByIdInFetchNoOrder(ids);
        java.util.Map<Long, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        list.sort(java.util.Comparator.comparingInt(x -> order.getOrDefault(x.getId(), Integer.MAX_VALUE)));
        // total 近似用总记录数
        long total = policyDataRepository.count();
        return new PageImpl<>(list, PageRequest.of(p, s), total);
    }

    @Override
    public org.springframework.data.domain.Page<PolicyData> hotPage(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        List<Long> ids = policyDataRepository.findTopIds(PageRequest.of(p, s));
        if (ids == null || ids.isEmpty()) return new PageImpl<>(java.util.Collections.emptyList(), PageRequest.of(p, s), 0);
        List<PolicyData> list = policyDataRepository.findByIdInFetch(ids);
        // 保持点击量排序
        list.sort((a, b) -> {
            long cb = b.getClicks() == null ? 0L : b.getClicks();
            long ca = a.getClicks() == null ? 0L : a.getClicks();
            int c = Long.compare(cb, ca);
            if (c != 0) return c;
            java.time.LocalDate da = a.getPublishDate();
            java.time.LocalDate db = b.getPublishDate();
            if (da != null || db != null) {
                if (da == null) return 1;
                if (db == null) return -1;
                int d = da.compareTo(db);
                if (d != 0) return d;
            }
            long ia = a.getId() == null ? Long.MAX_VALUE : a.getId();
            long ib = b.getId() == null ? Long.MAX_VALUE : b.getId();
            return Long.compare(ia, ib);
        });
        long total = policyDataRepository.count();
        return new PageImpl<>(list, PageRequest.of(p, s), total);
    }

    @Override
    public org.springframework.data.domain.Page<PolicyData> recommendPage(String username, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 50);
        List<PolicyData> list = recommendForUser(username, s); // 简化：按 size 取一页
        long total = list.size(); // 简化：暂无复杂推荐总量，返回当前批大小
        return new PageImpl<>(list, PageRequest.of(p, s), total);
    }

    @Override
    public long totalCount() { return policyDataRepository.count(); }

    @Override
    public List<PolicyData> recommendForUser(String username, int limit) {
        try {
            if (username == null || username.isBlank()) return latest(limit);
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) return latest(limit);
            Long uid = userOpt.get().getId();
            // 获取该用户最近若干次查看的政策，提取关键词（简单基于标题与摘要分词/关键词）
            // 由于未引入复杂分词，这里采用简单的英文/数字/中文连续字词统计，按频次取Top N
        int lookback = 50;
        var logs = userActionLogRepository.findByUserIdAndActionTypeOrderByActionTimeDesc(
            uid, "VIEW_DETAIL", org.springframework.data.domain.PageRequest.of(0, lookback));
            java.util.Set<Long> viewedIds = new java.util.HashSet<>();
            for (var l : logs) {
                if (l.getUserId() != null && l.getUserId().equals(uid) && l.getPolicyId() != null) viewedIds.add(l.getPolicyId());
            }
            if (viewedIds.isEmpty()) return latest(limit);
            // 拉取已看的条目的文本，构建词频
            var viewedList = policyDataRepository.findAllById(viewedIds);
            java.util.Map<String,Integer> freq = new java.util.HashMap<>();
            for (var p : viewedList) {
                String text = ((p.getZhTitle()==null?"":p.getZhTitle()+" ") + (p.getOriginalTitle()==null?"":p.getOriginalTitle()+" ") + (p.getSummary()==null?"":p.getSummary()))
                        .toLowerCase();
                // 粗略分词：字母数字或中文连续
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[a-zA-Z0-9]{3,}|[\\u4e00-\\u9fa5]{2,}").matcher(text);
                while(m.find()){
                    String w = m.group();
                    // 过滤常见停用词（可扩展）
                    if (w.length() < 2) continue;
                    if (java.util.Set.of("the","and","with","for","from","that","this","have","will","your","about").contains(w)) continue;
                    freq.put(w, freq.getOrDefault(w,0)+1);
                }
            }
            if (freq.isEmpty()) return latest(limit);
            // 取Top 3关键词，分别检索并合并结果（实现 OR 语义），按权重排序
            var top = freq.entrySet().stream()
                    .sorted((a,b)->Integer.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(java.util.Map.Entry::getKey)
                    .toList();
            java.util.Map<Long, Double> score = new java.util.HashMap<>();
            java.util.Map<Long, PolicyData> pool = new java.util.HashMap<>();
            for (String t : top) {
                var candidates = search(t, null, null, Math.max(limit, 20));
                double w = freq.getOrDefault(t, 1);
                for (var p : candidates) {
                    if (viewedIds.contains(p.getId())) continue; // 排除已看
                    pool.putIfAbsent(p.getId(), p);
                    score.put(p.getId(), score.getOrDefault(p.getId(), 0.0) + w);
                }
            }
            var merged = new java.util.ArrayList<PolicyData>(pool.values());
            // 综合排序：按累计权重、发布时间、点击量
            merged.sort((a,b)->{
                int c1 = java.lang.Double.compare(score.getOrDefault(b.getId(),0.0), score.getOrDefault(a.getId(),0.0));
                if (c1 != 0) return c1;
                java.time.LocalDate da = a.getPublishDate();
                java.time.LocalDate db = b.getPublishDate();
                if (da != null || db != null) {
                    if (da == null) return 1; if (db == null) return -1;
                    int c2 = db.compareTo(da);
                    if (c2 != 0) return c2;
                }
                long ca = a.getClicks() == null ? 0L : a.getClicks();
                long cb = b.getClicks() == null ? 0L : b.getClicks();
                return Long.compare(cb, ca);
            });
            if (merged.size() > limit) merged = new java.util.ArrayList<>(merged.subList(0, limit));
            if (merged.size() < Math.min(limit, 10)) {
                // 补齐最新
                for (var p : latest(limit)) {
                    if (merged.stream().anyMatch(x -> x.getId().equals(p.getId()))) continue;
                    if (!viewedIds.contains(p.getId())) merged.add(p);
                    if (merged.size() >= limit) break;
                }
            }
            return merged;
        } catch (Exception e) {
            return latest(limit);
        }
    }
}
