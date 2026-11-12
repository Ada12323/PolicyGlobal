package com.example.policy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "pdf_validation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfValidation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false, unique = true)
    private Long policyId;

    @Column(name = "status", nullable = false)
    private Integer status; // 0:未验证, 1:有效, 2:失效

    @Column(name = "check_result", length = 500)
    private String checkResult;

    @Column(name = "last_check_time")
    private LocalDateTime lastCheckTime;

    @Column(name = "check_time")
    private Long checkTime; // 验证耗时（毫秒）

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = 0; // 默认未验证
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}