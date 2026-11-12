package com.example.policy.controller;

import com.example.policy.dto.BatchDeleteRequestDTO;
import com.example.policy.dto.InvalidPolicyDTO;
import com.example.policy.dto.ValidationTaskDTO;
import com.example.policy.service.PdfValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/pdf-validation")
public class AdminPdfValidationController {

    @Autowired
    private PdfValidationService pdfValidationService;

    /**
     * 启动批量验证
     */
    @PostMapping("/start-batch")
    public ResponseEntity<?> startBatchValidation() {
        try {
            String taskId = pdfValidationService.startBatchValidation();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("message", "验证任务已启动");

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("启动验证任务失败: " + e.getMessage()));
        }
    }

    /**
     * 获取当前任务进度
     */
    @GetMapping("/current-progress")
    public ResponseEntity<?> getCurrentProgress() {
        try {
            ValidationTaskDTO currentTask = pdfValidationService.getCurrentTask();

            if (currentTask == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("hasTask", false);
                response.put("message", "当前没有正在运行的任务");
                return ResponseEntity.ok(response);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("hasTask", true);
            response.put("task", currentTask);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("获取任务进度失败: " + e.getMessage()));
        }
    }

    /**
     * 获取指定任务进度
     */
    @GetMapping("/progress/{taskId}")
    public ResponseEntity<?> getProgress(@PathVariable String taskId) {
        try {
            Long id = Long.parseLong(taskId);
            ValidationTaskDTO task = pdfValidationService.getValidationProgress(id);
            return ResponseEntity.ok(task);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(createErrorResponse("任务ID格式错误"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("获取任务进度失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有失效政策
     */
    @GetMapping("/invalid-policies")
    public ResponseEntity<?> getInvalidPolicies() {
        try {
            List<InvalidPolicyDTO> invalidPolicies = pdfValidationService.getInvalidPolicies();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", invalidPolicies.size());
            response.put("data", invalidPolicies);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("获取失效政策列表失败: " + e.getMessage()));
        }
    }

    /**
     * 删除失效政策
     */
    @PostMapping("/delete-invalid")
    public ResponseEntity<?> deleteInvalidPolicies(@RequestBody BatchDeleteRequestDTO request) {
        try {
            if (request.getPolicyIds() == null || request.getPolicyIds().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("请选择要删除的政策"));
            }

            // 二次确认 - 前端应该已经确认过了，这里再次检查
            if (request.getPolicyIds().size() > 10 && !Boolean.TRUE.equals(request.getConfirmed())) {
                return ResponseEntity.badRequest().body(createErrorResponse("批量删除超过10条记录需要二次确认"));
            }

            pdfValidationService.deleteInvalidPolicies(request.getPolicyIds());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "成功删除 " + request.getPolicyIds().size() + " 个失效政策");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("删除失败: " + e.getMessage()));
        }
    }

    /**
     * 取消验证任务
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        try {
            Long id = Long.parseLong(taskId);
            pdfValidationService.cancelValidationTask(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "任务已取消");

            return ResponseEntity.ok(response);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(createErrorResponse("任务ID格式错误"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("取消任务失败: " + e.getMessage()));
        }
    }

    /**
     * 获取验证统计
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getValidationStats() {
        try {
            // 获取当前失效政策数量
            List<InvalidPolicyDTO> invalidPolicies = pdfValidationService.getInvalidPolicies();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("invalidCount", invalidPolicies.size());
            response.put("lastCheckTime", invalidPolicies.isEmpty() ? null :
                    invalidPolicies.get(0).getLastCheckTime()); // 取第一个的检查时间

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(createErrorResponse("获取统计失败: " + e.getMessage()));
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}