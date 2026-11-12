package com.example.policy.repository;

import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PolicyOrgRepository extends JpaRepository<PolicyOrg, Long> {
    Optional<PolicyOrg> findByOrgName(String orgName);
    Optional<PolicyOrg> findByOrgNameIgnoreCase(String orgName);

    @Query("SELECT pd.org FROM PolicyData pd ")
    List<PolicyOrg> findsomeOrg();

    @Query("""
    SELECT DISTINCT pd.org 
    FROM PolicyData pd 
    INNER JOIN UserPolicyRelation upr ON pd.id = upr.policyId 
    LEFT JOIN PolicyOrg org ON pd.org.id = org.id 
    WHERE upr.userId = :userId
""")
    List<PolicyOrg> findOrgByUserId(@Param("userId") Long userId);

    //根据机构的ids查询政策数据
    @Query("SELECT p FROM PolicyData p LEFT JOIN FETCH p.org WHERE p.org.id IN :orgIds")
    List<PolicyData> findPolicyDataByOrgIds(@Param("orgIds") List<Long> orgIds);

    @Query("""
        SELECT p, COALESCE(AVG(r.rating), 0) as avgRating 
        FROM PolicyData p 
        LEFT JOIN Rating r ON p.id = r.policyId 
        LEFT JOIN FETCH p.org 
        WHERE p.org.id IN :orgIds 
        GROUP BY p.id, p.zhTitle, p.originalTitle, p.summary, p.publishDate, 
                 p.sourceUrl, p.pdfUrl, p.clicks, p.org, p.createdAt, p.updatedAt 
        ORDER BY avgRating DESC NULLS LAST, p.publishDate DESC
    """)
    List<Object[]> findPolicyDataWithRatingByOrgIds(@Param("orgIds") List<Long> orgIds);

    /**
     * 查询指定机构+指定用户关联的政策数据（保留原有评分计算逻辑）
     * @param orgIds 机构ID列表
     * @param userId 用户ID
     * @return List<Object[]>：数组第一个元素是PolicyData，第二个是平均评分（Double）
     */
    @Query("""
        SELECT p, COALESCE(AVG(r.rating), 0) as avgRating 
        FROM PolicyData p 
        
       INNER JOIN UserPolicyRelation upr ON p.id = upr.policyId AND upr.userId = :userId
        LEFT JOIN Rating r ON p.id = r.policyId 
        LEFT JOIN FETCH p.org 
        WHERE p.org.id IN :orgIds 
        GROUP BY p.id, p.zhTitle, p.originalTitle, p.summary, p.publishDate, 
                 p.sourceUrl, p.pdfUrl, p.clicks, p.org, p.createdAt, p.updatedAt 
        ORDER BY avgRating DESC NULLS LAST, p.publishDate DESC
    """)
    List<Object[]> findPolicyDataWithRatingByOrgIdsAndUserId(
            @Param("orgIds") List<Long> orgIds,
            @Param("userId") Long userId
    );
}
