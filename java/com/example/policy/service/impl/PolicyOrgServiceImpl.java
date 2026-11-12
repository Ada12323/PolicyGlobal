package com.example.policy.service.impl;

import com.example.policy.dto.PolicyDataWithRatingDTO;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import com.example.policy.repository.PolicyOrgRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.PolicyOrgService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class PolicyOrgServiceImpl implements PolicyOrgService {
    private final PolicyOrgRepository policyOrgRepository;
    private final UserRepository userRepository;

    public PolicyOrgServiceImpl(PolicyOrgRepository policyOrgRepository, UserRepository userRepository) {
        this.policyOrgRepository = policyOrgRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<PolicyOrg> findsomeOrg() {
        return policyOrgRepository.findsomeOrg();
    }

    @Override
    public List<PolicyData> findPolicyDataByOrgIds(String orgIds) {
        List<Long> orgIdsList = Arrays.stream(orgIds.split(","))
                .map(Long::parseLong) // 转换为Long类型
                .toList();
        return policyOrgRepository.findPolicyDataByOrgIds(orgIdsList);
    }

    @Override
    public List<PolicyDataWithRatingDTO> findPolicyDataWithRatingByOrgIds(String orgIds) {
        List<Long> orgIdsList = Arrays.stream(orgIds.split(","))
                .map(Long::parseLong)
                .toList();

        // 执行查询，返回Object[]数组，第一个元素是PolicyData，第二个元素是平均评分
        List<Object[]> results = policyOrgRepository.findPolicyDataWithRatingByOrgIds(orgIdsList);

        // 转换为DTO
        return results.stream()
                .map(result -> {
                    PolicyData policyData = (PolicyData) result[0];
                    Double averageRating = (Double) result[1];
                    return PolicyDataWithRatingDTO.fromPolicyData(policyData, averageRating);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PolicyDataWithRatingDTO> findPolicyDataWithRatingAndUserIdByOrgIds(String orgIds) {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        List<Long> orgIdsList = Arrays.stream(orgIds.split(","))
                .map(Long::parseLong)
                .toList();

        // 执行查询，返回Object[]数组，第一个元素是PolicyData，第二个元素是平均评分
        List<Object[]> results = policyOrgRepository.findPolicyDataWithRatingByOrgIdsAndUserId(orgIdsList, uid);

        // 转换为DTO
        return results.stream()
                .map(result -> {
                    PolicyData policyData = (PolicyData) result[0];
                    Double averageRating = (Double) result[1];
                    return PolicyDataWithRatingDTO.fromPolicyData(policyData, averageRating);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PolicyOrg> findsomeOrgMySearch() {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        return policyOrgRepository.findOrgByUserId(uid);
    }
}
