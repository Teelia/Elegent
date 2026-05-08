package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.request.AnalyzeTaskRequest;
import com.datalabeling.dto.response.ModelConfigVO;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.Label;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.service.constant.TaskStatus;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务分析编排服务（同步校验 + 提交异步任务）
 */
@Service
@RequiredArgsConstructor
public class TaskAnalyzeService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelRepository labelRepository;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;
    private final TaskAnalyzeAsyncService taskAnalyzeAsyncService;
    private final ModelConfigService modelConfigService;

    @Transactional(rollbackFor = Exception.class)
    public void startAnalyze(Integer taskId, AnalyzeTaskRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (TaskStatus.ARCHIVED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "任务已归档，禁止重新分析");
        }
        if (TaskStatus.PROCESSING.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_PROCESSING);
        }
        if (!TaskStatus.UPLOADED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "仅支持对 uploaded 状态的任务发起分析");
        }

        long existingRows = dataRowRepository.countByTaskId(taskId);
        if (existingRows > 0) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "任务已存在结果数据，禁止重复分析");
        }

        List<Integer> labelIds = request.getLabelIds();
        if (labelIds == null || labelIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少选择一个标签");
        }

        // 绑定标签版本快照
        for (Integer labelId : labelIds) {
            Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

            if (!securityUtil.isAdmin() && !currentUserId.equals(label.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能使用自己创建的标签");
            }

            if (taskLabelRepository.existsByTaskIdAndLabelId(taskId, labelId)) {
                continue;
            }

            TaskLabel taskLabel = TaskLabel.builder()
                .taskId(taskId)
                .labelId(label.getId())
                .labelName(label.getName())
                .labelVersion(label.getVersion())
                .labelDescription(label.getDescription())
                .build();
            taskLabelRepository.save(taskLabel);
        }

        // 校验并保存模型配置
        if (request.getModelConfigId() != null) {
            ModelConfigVO config = modelConfigService.getConfigById(request.getModelConfigId());
            task.setRunModelConfigName(config.getName());
        }
        task.setRunIncludeReasoning(Boolean.TRUE.equals(request.getIncludeReasoning()));

        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        task.setProcessedRows(0);
        task.setFailedRows(0);
        task.setErrorMessage(null);
        fileTaskRepository.save(task);

        Map<String, Object> details = new HashMap<>();
        details.put("labelIds", labelIds);
        auditService.record("start_analysis", "task", taskId, details, httpRequest);

        // 异步启动分析
        taskAnalyzeAsyncService.analyzeAsync(taskId, request.getModelConfigId(), Boolean.TRUE.equals(request.getIncludeReasoning()));
    }
}

