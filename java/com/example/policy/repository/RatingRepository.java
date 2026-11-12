package com.example.policy.repository;


import com.example.policy.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Integer> {
    @Query("SELECT r.rating FROM Rating r WHERE r.policyId = :policyId AND r.userId = :userId")
    Optional<String> findByUserIdAndPolicyId(@Param("userId") Long userId, @Param("policyId") Long policyId);

    //根据policyId查询平均分
    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.policyId = :policyId")
    Optional<Double> findAverageRatingByPolicyId(@Param("policyId") Long policyId);

    //根据policyId查询评分数量
    @Query("SELECT COUNT(r.id) FROM Rating r WHERE r.policyId = :policyId")
    Optional<Long> findCountRatingByPolicyId(@Param("policyId") Long policyId);

}
