package com.example.policy.service.impl;

import com.example.policy.repository.UserPolicyRelationRepository;
import com.example.policy.repository.UserRepository;
import com.example.policy.service.UserPolicyRelationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class UserPolicyRelationServiceImpl implements UserPolicyRelationService {

    private final UserRepository userRepository;
    private final UserPolicyRelationRepository userPolicyRelationRepository;

    public UserPolicyRelationServiceImpl(UserRepository userRepository, UserPolicyRelationRepository userPolicyRelationRepository) {
        this.userRepository = userRepository;
        this.userPolicyRelationRepository = userPolicyRelationRepository;
    }

    @Override
    public void deleteUserPolicyRelation(Long PolicyId) {
        Long uid;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // 从数据库查询用户信息，提取 ID（若不存在则返回 null）
        uid = userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
        userPolicyRelationRepository.deleteByUserIdAndPolicyId(uid, PolicyId);
    }
}
