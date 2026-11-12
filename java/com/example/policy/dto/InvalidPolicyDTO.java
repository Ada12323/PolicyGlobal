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
public class InvalidPolicyDTO {
    private Long id;
    private String zhTitle;
    private String originalTitle;
    private String pdfUrl;
    private String orgName;
    private String checkResult;
    private LocalDateTime lastCheckTime;
    private Long checkTime;

    // 格式化检查时间
    public String getFormattedCheckTime() {
        if (checkTime == null || checkTime == 0) {
            return "N/A";
        }
        if (checkTime < 1000) {
            return checkTime + "ms";
        }
        return String.format("%.2fs", checkTime / 1000.0);
    }
}