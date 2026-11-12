package com.example.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteRequestDTO {
    private List<Long> policyIds;
    private Boolean confirmed; // 是否已二次确认

    public BatchDeleteRequestDTO(List<Long> policyIds) {
        this.policyIds = policyIds;
        this.confirmed = false;
    }
}