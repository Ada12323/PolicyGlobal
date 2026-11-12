package com.example.policy.service;

import com.example.policy.entity.PolicyData;
import com.example.policy.repository.PolicyDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DedupService {
    private static final Logger log = LoggerFactory.getLogger(DedupService.class);
    private final PolicyDataRepository policyDataRepository;

    public DedupService(PolicyDataRepository policyDataRepository) {
        this.policyDataRepository = policyDataRepository;
    }

    public static class DedupResult {
        public int groups;        // 重复分组数
        public int removed;       // 删除的记录数
        public int retained;      // 保留的记录数
        public long mergedClicks; // 合并的点击增量
        public List<Map<String,Object>> details = new ArrayList<>();
    }

    @Transactional
    public DedupResult deduplicateByPdfUrl() {
        DedupResult result = new DedupResult();
        List<Object[]> keys = policyDataRepository.findDuplicatePdfUrlKeys();
        result.groups = keys.size();
        for (Object[] row : keys) {
            String key = String.valueOf(row[0]);
            List<PolicyData> list = policyDataRepository.findByNormalizedPdf(key);
            if (list.isEmpty()) continue;
            // 选择保留项：优先最新 publishDate；再按 createdAt 最早；再按 id 最小
            list.sort(
                Comparator.comparing(PolicyData::getPublishDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed()
                    .thenComparing(PolicyData::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(p -> Optional.ofNullable(p.getId()).orElse(Long.MAX_VALUE))
            );
            PolicyData keep = list.get(0);
            long clicksSum = 0L;
            List<Long> removedIds = new ArrayList<>();
            for (int i = 1; i < list.size(); i++) {
                PolicyData dup = list.get(i);
                clicksSum += Optional.ofNullable(dup.getClicks()).orElse(0L);
                removedIds.add(dup.getId());
            }
            // 合并点击到保留记录
            if (clicksSum > 0) {
                long base = Optional.ofNullable(keep.getClicks()).orElse(0L);
                keep.setClicks(base + clicksSum);
                policyDataRepository.save(keep);
                result.mergedClicks += clicksSum;
            }
            // 删除其它重复记录
            if (!removedIds.isEmpty()) {
                policyDataRepository.deleteAllById(removedIds);
                result.removed += removedIds.size();
            }
            result.retained++;
            Map<String,Object> det = new HashMap<>();
            det.put("normalizedPdf", key);
            det.put("keptId", keep.getId());
            det.put("removedIds", removedIds);
            det.put("mergedClicks", clicksSum);
            result.details.add(det);
            log.info("Dedup group {}: keep id={}, removed={}, mergedClicks={}", key, keep.getId(), removedIds, clicksSum);
        }
        return result;
    }
}
