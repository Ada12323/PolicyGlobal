 package com.example.policy.service;

import com.example.policy.entity.PolicyData;
import java.time.LocalDate;
import java.util.List;

public interface  PolicyDataService {
    List<PolicyData> search(String keyword, LocalDate start, LocalDate end, int limit);
    PolicyData getDetail(Long id, boolean increaseClicks);
    List<Object[]> aggregateByOrg();
    List<PolicyData> hot(int limit);
    List<PolicyData> latest(int limit);
    List<PolicyData> recommendForUser(String username, int limit);
    // 分页支持
    org.springframework.data.domain.Page<PolicyData> latestPage(int page, int size);
    org.springframework.data.domain.Page<PolicyData> hotPage(int page, int size);
    org.springframework.data.domain.Page<PolicyData> recommendPage(String username, int page, int size);
    long totalCount();
}