package com.example.policy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false)
    private String taskName;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "processed_count")
    private Integer processedCount;

    @Column(name = "valid_count")
    private Integer validCount;

    @Column(name = "invalid_count")
    private Integer invalidCount;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
        if (processedCount == null) processedCount = 0;
        if (validCount == null) validCount = 0;
        if (invalidCount == null) invalidCount = 0;
    }
}