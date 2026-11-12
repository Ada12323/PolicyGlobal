package com.example.policy.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rating")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "policy_id")
    private Long policyId;
    @Column(name = "rating")
    private Double rating;
    @Column(name = "action_time")
    private LocalDateTime actionTime;

    @PrePersist
    public void prePersist() {
        if (actionTime == null) actionTime = LocalDateTime.now();
    }

}
