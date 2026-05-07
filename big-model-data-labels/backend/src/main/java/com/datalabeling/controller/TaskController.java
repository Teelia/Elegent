package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.AnalyzeTaskRequest;
import com.datalabeling.dto.response.FileUploadResponse;
import com.datalabeling.dto.response.FileTaskVO;
import com.datalabeling.dto.response.TaskProgressVO;
import com.datalabeling.service.AuditService;
import com.datalabeling.service.FileTaskService;
import com.datalabeling.service.TaskAnalyzeService;
import com.datalabeling.service.TaskExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 任务控制器
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final FileTaskService fileTaskService;
    private final TaskAnalyzeService taskAnalyzeService;
    private final TaskExportService taskExportService;
    private final AuditService auditService;

    /**
     * 上传文件并返回预览
     */
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
        @RequestParam("file") MultipartFile file,
        HttpServletRequest request
    ) {
        FileUploadResponse response = fileTaskService.upload(file, request);
        return ApiResponse.success("上传成功", response);
    }

    /**
     * 配置任务标签（不启动分析）
     * 仅允许 uploaded 或 pending 状态的任务配置标签
     */
    @PostMapping("/{id}/labels")
    public ApiResponse<FileTaskVO> configureLabels(
        @PathVariable("id") Integer taskId,
        @RequestBody Map<String, List<Integer>> request,
        HttpServletRequest httpRequest
    ) {
        List<Integer> labelIds = request.get("labelIds");
        if (labelIds == null) {
            labelIds = Collections.emptyList();
        }
        FileTaskVO result = fileTaskService.configureLabels(taskId, labelIds, httpRequest);
        return ApiResponse.success("标签配置成功", result);
    }

    /**
     * 移除任务标签
     * 仅允许 pending 状态的任务移除标签
     */
    @DeleteMapping("/{id}/labels/{labelId}")
    public ApiResponse<Void> removeLabel(
        @PathVariable("id") Integer taskId,
        @PathVariable("labelId") Integer labelId,
        HttpServletRequest request
    ) {
        fileTaskService.removeLabel(taskId, labelId, request);
        return ApiResponse.success("标签移除成功", null);
    }

    /**
     * 启动任务分析
     * 仅允许 pending 状态的任务启动
     */
    @PostMapping("/{id}/start")
    public ApiResponse<FileTaskVO> startTask(
        @PathVariable("id") Integer taskId,
        @RequestBody(required = false) AnalyzeTaskRequest request,
        HttpServletRequest httpRequest
    ) {
        FileTaskVO result = fileTaskService.startTask(taskId, request, httpRequest);
        return ApiResponse.success("任务已启动", result);
    }

    /**
     * 暂停任务
     */
    @PostMapping("/{id}/pause")
    public ApiResponse<FileTaskVO> pauseTask(
        @PathVariable("id") Integer taskId,
        HttpServletRequest request
    ) {
        FileTaskVO result = fileTaskService.pauseTask(taskId, request);
        return ApiResponse.success("任务已暂停", result);
    }

    /**
     * 继续任务（从暂停状态恢复）
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<FileTaskVO> resumeTask(
        @PathVariable("id") Integer taskId,
        HttpServletRequest request
    ) {
        FileTaskVO result = fileTaskService.resumeTask(taskId, request);
        return ApiResponse.success("任务已继续", result);
    }

    /**
     * 重新启动任务（用于completed/failed/cancelled状态）
     */
    @PostMapping("/{id}/restart")
    public ApiResponse<FileTaskVO> restartTask(
        @PathVariable("id") Integer taskId,
        @RequestBody(required = false) AnalyzeTaskRequest request,
        HttpServletRequest httpRequest
    ) {
        FileTaskVO result = fileTaskService.restartTask(taskId, request, httpRequest);
        return ApiResponse.success("任务已重新启动", result);
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelTask(
        @PathVariable("id") Integer taskId,
        HttpServletRequest request
    ) {
        fileTaskService.cancelTask(taskId, request);
        return ApiResponse.success("任务已取消", null);
    }

    /**
     * 发起异步分析（兼容旧接口）
     */
    @PostMapping("/{id}/analyze")
    public ApiResponse<Void> analyze(
        @PathVariable("id") Integer taskId,
        @Validated @RequestBody AnalyzeTaskRequest request,
        HttpServletRequest httpRequest
    ) {
        taskAnalyzeService.startAnalyze(taskId, request, httpRequest);
        return ApiResponse.success("已开始分析", null);
    }

    /**
     * 获取任务进度（轮询兜底）
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<TaskProgressVO> progress(@PathVariable("id") Integer taskId, HttpServletRequest request) {
        return ApiResponse.success(fileTaskService.getProgress(taskId, request));
    }

    /**
     * 获取数据预览（列统计）
     */
    @GetMapping("/{id}/preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable("id") Integer taskId, HttpServletRequest request) {
        return ApiResponse.success(fileTaskService.getTaskPreview(taskId, request));
    }

    /**
     * 任务列表（分页）
     */
    @GetMapping
    public ApiResponse<PageResult<FileTaskVO>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Integer userId,
        HttpServletRequest request
    ) {
        return ApiResponse.success(fileTaskService.getTaskList(page, size, status, userId, request));
    }

    /**
     * 任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<FileTaskVO> detail(@PathVariable("id") Integer taskId, HttpServletRequest request) {
        return ApiResponse.success(fileTaskService.getTaskDetail(taskId, request));
    }

    /**
     * 任务归档（归档后只读）
     */
    @PostMapping("/{id}/archive")
    public ApiResponse<Void> archive(@PathVariable("id") Integer taskId, HttpServletRequest request) {
        fileTaskService.archive(taskId, request);
        return ApiResponse.success("归档成功", null);
    }

    /**
     * 创建任务临时标签
     * 创建一个只属于当前任务的临时标签，可用于分析
     */
    @PostMapping("/{id}/temp-labels")
    public ApiResponse<com.datalabeling.dto.response.LabelVO> createTempLabel(
        @PathVariable("id") Integer taskId,
        @RequestBody Map<String, Object> request,
        HttpServletRequest httpRequest
    ) {
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        @SuppressWarnings("unchecked")
        List<String> focusColumns = (List<String>) request.get("focusColumns");
        
        com.datalabeling.dto.response.LabelVO result = fileTaskService.createTempLabel(
            taskId, name, description, focusColumns, httpRequest);
        return ApiResponse.success("临时标签创建成功", result);
    }

    /**
     * 将任务临时标签保存到全局标签库
     */
    @PostMapping("/{id}/temp-labels/{labelId}/promote")
    public ApiResponse<com.datalabeling.dto.response.LabelVO> promoteTempLabel(
        @PathVariable("id") Integer taskId,
        @PathVariable("labelId") Integer labelId,
        HttpServletRequest httpRequest
    ) {
        com.datalabeling.dto.response.LabelVO result = fileTaskService.promoteTempLabelToGlobal(
            taskId, labelId, httpRequest);
        return ApiResponse.success("标签已保存到标签库", result);
    }

    /**
     * 获取任务的临时标签列表
     */
    @GetMapping("/{id}/temp-labels")
    public ApiResponse<List<com.datalabeling.dto.response.LabelVO>> getTempLabels(
        @PathVariable("id") Integer taskId,
        HttpServletRequest httpRequest
    ) {
        List<com.datalabeling.dto.response.LabelVO> result = fileTaskService.getTempLabels(taskId, httpRequest);
        return ApiResponse.success(result);
    }

    /**
     * 更新单行信心度阈值
     */
    @PutMapping("/{id}/rows/{rowId}/threshold")
    public ApiResponse<Void> updateRowThreshold(
        @PathVariable("id") Integer taskId,
        @PathVariable("rowId") Long rowId,
        @RequestBody Map<String, BigDecimal> request,
        HttpServletRequest httpRequest
    ) {
        BigDecimal threshold = request.get("confidenceThreshold");
        if (threshold == null) {
            threshold = new BigDecimal("0.80");
        }
        fileTaskService.updateRowThreshold(taskId, rowId, threshold, httpRequest);
        return ApiResponse.success("阈值更新成功", null);
    }

    /**
     * 导出Excel
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable("id") Integer taskId, HttpServletRequest request) {
        TaskExportService.ExportDownload download = taskExportService.exportDownload(taskId);
        auditService.record("export_excel", "task", taskId, null, request);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + download.getFileName())
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(download.getBody());
    }
}
