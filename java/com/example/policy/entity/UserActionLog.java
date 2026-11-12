package com.example.policy.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_action_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "action_type", length = 50, nullable = false)
    private String actionType;

    @Column(name = "action_time")
    private LocalDateTime actionTime;

    @Column(name = "extra_json", columnDefinition = "JSON")
    private String extraJson;

    @PrePersist
    public void prePersist() {
        if (actionTime == null) actionTime = LocalDateTime.now();
    }
}
