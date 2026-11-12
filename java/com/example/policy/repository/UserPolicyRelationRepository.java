package com.example.policy.repository;

import com.example.policy.entity.UserPolicyRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserPolicyRelationRepository extends JpaRepository<UserPolicyRelation, Long> {

    // 判断用户是否已关联某政策（核心去重判断）
    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    // 根据PolicyId和UserId删除
    @Transactional
    void deleteByUserIdAndPolicyId(Long userId, Long policyId);

    // 查询某用户关联的所有政策ID（核心查询）
    List<Long> findPolicyIdByUserId(Long userId);

    // 查询某政策关联的所有用户ID（辅助统计）
    List<Long> findUserIdByPolicyId(Long policyId);
}
