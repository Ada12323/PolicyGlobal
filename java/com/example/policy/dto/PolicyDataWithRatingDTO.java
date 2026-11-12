package com.example.policy.dto;

import com.example.policy.entity.PolicyData;
import com.example.policy.entity.PolicyOrg;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDataWithRatingDTO {
    private Long id;
    private String zhTitle;
    private String originalTitle;
    private String summary;
    private LocalDate publishDate;
    private String sourceUrl;
    private String pdfUrl;
    private Long clicks;
    private PolicyOrg org;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double averageRating; // 平均评分，可能为null

    // 从PolicyData实体构造的便捷方法
    public static PolicyDataWithRatingDTO fromPolicyData(PolicyData policyData, Double averageRating) {
        if (policyData == null) {
            return null;
        }

        return new PolicyDataWithRatingDTO(
                policyData.getId(),
                policyData.getZhTitle(),
                policyData.getOriginalTitle(),
                policyData.getSummary(),
                policyData.getPublishDate(),
                policyData.getSourceUrl(),
                policyData.getPdfUrl(),
                policyData.getClicks(),
                policyData.getOrg(),
                policyData.getCreatedAt(),
                policyData.getUpdatedAt(),
                averageRating
        );
    }
}