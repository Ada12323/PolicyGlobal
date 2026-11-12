package com.example.policy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Entity
@Table(name = "user_policy_relation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPolicyRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联用户ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 关联政策ID
    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    // 创建时间（自动填充）
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    // 更新时间（自动更新）
    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    // 自动填充时间字段
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }
        if (this.updatedAt == null) {
            this.updatedAt = new Timestamp(System.currentTimeMillis());
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
