package com.example.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationTaskDTO {
    private Long id;
    private String taskName;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer validCount;
    private Integer invalidCount;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;

    // 计算进度百分比
    public Integer getProgressPercentage() {
        if (totalCount == null || totalCount == 0) {
            return 0;
        }
        if (processedCount == null) {
            return 0;
        }
        return (int) Math.round((processedCount * 100.0) / totalCount);
    }
}