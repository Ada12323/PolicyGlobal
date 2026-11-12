package com.example.policy.service;

import com.example.policy.dto.InvalidPolicyDTO;
import com.example.policy.dto.ValidationTaskDTO;

import java.util.List;

public interface PdfValidationService {

    /**
     * 启动批量验证任务
     * @return 任务ID
     */
    String startBatchValidation();

    /**
     * 获取验证任务进度
     * @param taskId 任务ID
     * @return 任务进度信息
     */
    ValidationTaskDTO getValidationProgress(Long taskId);

    /**
     * 获取当前正在运行的任务
     * @return 任务信息，如果没有则返回null
     */
    ValidationTaskDTO getCurrentTask();

    /**
     * 获取所有验证失效的政策
     * @return 失效政策列表
     */
    List<InvalidPolicyDTO> getInvalidPolicies();

    /**
     * 删除失效政策
     * @param policyIds 政策ID列表
     */
    void deleteInvalidPolicies(List<Long> policyIds);

    /**
     * 取消验证任务
     * @param taskId 任务ID
     */
    void cancelValidationTask(Long taskId);
}