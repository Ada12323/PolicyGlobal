package com.example.policy.service.impl;

import com.example.policy.dto.InvalidPolicyDTO;
import com.example.policy.dto.ValidationTaskDTO;
import com.example.policy.entity.PdfValidation;
import com.example.policy.entity.PolicyData;
import com.example.policy.entity.ValidationTask;
import com.example.policy.repository.PdfValidationRepository;
import com.example.policy.repository.PolicyDataRepository;
import com.example.policy.repository.ValidationTaskRepository;
import com.example.policy.service.PdfValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PdfValidationServiceImpl implements PdfValidationService {

    @Autowired
    private PolicyDataRepository policyDataRepository;

    @Autowired
    private PdfValidationRepository pdfValidationRepository;

    @Autowired
    private ValidationTaskRepository validationTaskRepository;

    @Autowired
    private PdfUrlValidator pdfUrlValidator;

    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    private static final int BATCH_SIZE = 50;

    @Override
    @Transactional
    public String startBatchValidation() {
        // 检查是否有正在运行的任务
        Optional<ValidationTask> runningTask = validationTaskRepository.findRunningTask();
        if (runningTask.isPresent()) {
            throw new IllegalStateException("已有验证任务正在运行，请等待完成或取消后再试");
        }

        // 创建新任务
        String taskName = "PDF验证任务-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        ValidationTask task = ValidationTask.builder()
                .taskName(taskName)
                .status("PENDING")
                .startTime(LocalDateTime.now())
                .build();

        task = validationTaskRepository.save(task);
        log.info("创建验证任务: {}", task.getId());

        // 异步执行验证任务
        executeValidationTaskAsync(task.getId());

        return String.valueOf(task.getId());
    }

    @Async
    protected void executeValidationTaskAsync(Long taskId) {
        try {
            ValidationTask task = validationTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

            // 更新任务状态为运行中
            task.setStatus("RUNNING");
            validationTaskRepository.save(task);

            // 查询所有有PDF链接的政策
            List<PolicyData> allPolicies = policyDataRepository.findAll();
            List<PolicyData> policiesWithPdf = allPolicies.stream()
                    .filter(p -> p.getPdfUrl() != null && !p.getPdfUrl().trim().isEmpty())
                    .collect(Collectors.toList());

            task.setTotalCount(policiesWithPdf.size());
            validationTaskRepository.save(task);

            log.info("开始验证 {} 个PDF链接", policiesWithPdf.size());

            // 分批次验证
            for (int i = 0; i < policiesWithPdf.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, policiesWithPdf.size());
                List<PolicyData> batch = policiesWithPdf.subList(i, end);

                validateBatch(batch, task);

                // 每批次更新一次进度
                task.setProcessedCount(Math.min(end, policiesWithPdf.size()));
                validationTaskRepository.save(task);

                // 检查是否需要取消
                ValidationTask currentTask = validationTaskRepository.findById(taskId).orElse(null);
                if (currentTask != null && "CANCELLED".equals(currentTask.getStatus())) {
                    log.info("任务 {} 被取消", taskId);
                    return;
                }
            }

            // 任务完成
            task.setStatus("COMPLETED");
            task.setEndTime(LocalDateTime.now());
            validationTaskRepository.save(task);

            log.info("验证任务 {} 完成，总共验证 {} 个链接", taskId, policiesWithPdf.size());

        } catch (Exception e) {
            log.error("验证任务执行失败: {}", taskId, e);

            validationTaskRepository.findById(taskId).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setErrorMessage(e.getMessage());
                task.setEndTime(LocalDateTime.now());
                validationTaskRepository.save(task);
            });
        }
    }

    private void validateBatch(List<PolicyData> batch, ValidationTask task) {
        CompletableFuture<?>[] futures = batch.stream()
                .map(policy -> CompletableFuture.runAsync(() -> {
                    try {
                        validateSinglePolicy(policy, task.getId());
                    } catch (Exception e) {
                        log.error("验证政策 {} 失败: {}", policy.getId(), e.getMessage());
                    }
                }, executorService))
                .toArray(CompletableFuture[]::new);

        // 等待本批次全部完成
        CompletableFuture.allOf(futures).join();
    }

    private void validateSinglePolicy(PolicyData policy, Long taskId) {
        long startTime = System.currentTimeMillis();

        try {
            PdfUrlValidator.ValidationResult result = pdfUrlValidator.validateUrl(policy.getPdfUrl());

            PdfValidation validation = pdfValidationRepository.findByPolicyId(policy.getId())
                    .orElse(new PdfValidation());

            validation.setPolicyId(policy.getId());
            validation.setStatus(result.isValid() ? 1 : 2);
            validation.setCheckResult(result.getErrorMessage());
            validation.setLastCheckTime(LocalDateTime.now());
            validation.setCheckTime(result.getResponseTime());

            pdfValidationRepository.save(validation);

            // 更新任务统计
            ValidationTask task = validationTaskRepository.findById(taskId).orElse(null);
            if (task != null) {
                if (result.isValid()) {
                    task.setValidCount(task.getValidCount() + 1);
                } else {
                    task.setInvalidCount(task.getInvalidCount() + 1);
                }
                validationTaskRepository.save(task);
            }

            log.debug("验证政策 {}: {}, 耗时 {}ms",
                    policy.getId(), result.isValid() ? "有效" : "失效",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("验证政策 {} 异常: {}", policy.getId(), e.getMessage());
        }
    }

    @Override
    public ValidationTaskDTO getValidationProgress(Long taskId) {
        return validationTaskRepository.findById(taskId)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
    }

    @Override
    public ValidationTaskDTO getCurrentTask() {
        return validationTaskRepository.findRunningTask()
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    public List<InvalidPolicyDTO> getInvalidPolicies() {
        List<PdfValidation> invalidValidations = pdfValidationRepository.findAllInvalid();

        // 获取对应的政策ID
        List<Long> policyIds = invalidValidations.stream()
                .map(PdfValidation::getPolicyId)
                .collect(Collectors.toList());

        if (policyIds.isEmpty()) {
            return List.of();
        }

        // 查询政策数据
        List<PolicyData> policies = policyDataRepository.findByIdInFetchNoOrder(policyIds);

        // 构建DTO
        return policies.stream().map(policy -> {
            PdfValidation validation = invalidValidations.stream()
                    .filter(v -> v.getPolicyId().equals(policy.getId()))
                    .findFirst()
                    .orElse(null);

            return InvalidPolicyDTO.builder()
                    .id(policy.getId())
                    .zhTitle(policy.getZhTitle())
                    .originalTitle(policy.getOriginalTitle())
                    .pdfUrl(policy.getPdfUrl())
                    .orgName(policy.getOrg() != null ? policy.getOrg().getOrgName() : "")
                    .checkResult(validation != null ? validation.getCheckResult() : "未知")
                    .lastCheckTime(validation != null ? validation.getLastCheckTime() : null)
                    .checkTime(validation != null ? validation.getCheckTime() : 0L)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteInvalidPolicies(List<Long> policyIds) {
        if (policyIds == null || policyIds.isEmpty()) {
            return;
        }

        // 删除政策数据
        policyDataRepository.deleteAllById(policyIds);

        // 删除验证记录
        pdfValidationRepository.deleteByPolicyIds(policyIds);

        log.info("删除 {} 个失效政策", policyIds.size());
    }

    @Override
    @Transactional
    public void cancelValidationTask(Long taskId) {
        ValidationTask task = validationTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        if ("RUNNING".equals(task.getStatus())) {
            task.setStatus("CANCELLED");
            task.setEndTime(LocalDateTime.now());
            validationTaskRepository.save(task);
            log.info("任务 {} 已取消", taskId);
        }
    }

    private ValidationTaskDTO convertToDTO(ValidationTask task) {
        return ValidationTaskDTO.builder()
                .id(task.getId())
                .taskName(task.getTaskName())
                .status(task.getStatus())
                .totalCount(task.getTotalCount())
                .processedCount(task.getProcessedCount())
                .validCount(task.getValidCount())
                .invalidCount(task.getInvalidCount())
                .errorMessage(task.getErrorMessage())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .createdAt(task.getCreatedAt())
                .build();
    }
}