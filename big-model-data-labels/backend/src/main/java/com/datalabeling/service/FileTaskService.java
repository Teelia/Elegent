package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.FileTaskMapper;
import com.datalabeling.dto.mapper.LabelMapper;
import com.datalabeling.dto.request.AnalyzeTaskRequest;
import com.datalabeling.dto.response.FileUploadResponse;
import com.datalabeling.dto.response.FileTaskVO;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.dto.response.TaskProgressVO;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.Label;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.service.constant.TaskStatus;
import com.datalabeling.service.model.FilePreviewResult;
import com.datalabeling.util.FileUtil;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 文件任务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileTaskService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelRepository labelRepository;
    private final FileUtil fileUtil;
    private final FileParseService fileParseService;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;
    private final FileTaskMapper fileTaskMapper;
    private final LabelMapper labelMapper;

    /**
     * 上传文件并返回预览
     */
    @Transactional(rollbackFor = Exception.class)
    public FileUploadResponse upload(MultipartFile file, HttpServletRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String filePath = fileUtil.saveUploadFile(file, userId);
        String filename = Paths.get(filePath).getFileName().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : filename;

        FilePreviewResult previewResult = fileParseService.parsePreview(filePath, 20);

        FileTask task = FileTask.builder()
            .userId(userId)
            .filename(filename)
            .originalFilename(originalFilename)
            .filePath(filePath)
            .fileSize(file.getSize())
            .fileHash(calculateSha256(filePath))
            .columns(previewResult.getColumns())
            .status(TaskStatus.UPLOADED)
            .totalRows(previewResult.getTotalRows())
            .processedRows(0)
            .failedRows(0)
            .build();
        task = fileTaskRepository.save(task);

        Map<String, Object> details = new HashMap<>();
        details.put("filename", task.getFilename());
        details.put("originalFilename", task.getOriginalFilename());
        details.put("fileSize", task.getFileSize());
        details.put("totalRows", task.getTotalRows());
        auditService.record("upload_file", "task", task.getId(), details, request);

        return FileUploadResponse.builder()
            .taskId(task.getId())
            .filename(task.getOriginalFilename())
            .fileSize(task.getFileSize())
            .totalRows(task.getTotalRows())
            .columns(previewResult.getColumns())
            .preview(previewResult.getPreview())
            .build();
    }

    /**
     * 获取任务进度（轮询兜底）
     */
    public TaskProgressVO getProgress(Integer taskId, HttpServletRequest httpRequest) {
        Integer userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("taskId", taskId);
        auditService.recordAdminRead(task.getUserId(), "admin_read_task_progress", "task", taskId, auditDetails, httpRequest);

        int total = task.getTotalRows() != null ? task.getTotalRows() : 0;
        int processed = task.getProcessedRows() != null ? task.getProcessedRows() : 0;
        int failed = task.getFailedRows() != null ? task.getFailedRows() : 0;
        int percentage = total > 0 ? (int) Math.min(100L, Math.round(processed * 100.0 / total)) : 0;

        return TaskProgressVO.builder()
            .taskId(taskId)
            .total(total)
            .processed(processed)
            .failed(failed)
            .percentage(percentage)
            .currentRow(processed)
            .etaSeconds(null)
            .status(task.getStatus())
            .build();
    }

    /**
     * 获取任务列表（分页）
     */
    public PageResult<FileTaskVO> getTaskList(Integer page, Integer size, String status, Integer queryUserId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size,
            Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean isAdmin = securityUtil.isAdmin();
        Integer userId = isAdmin ? queryUserId : currentUserId;

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("page", page);
        auditDetails.put("size", size);
        auditDetails.put("status", status);
        auditService.recordAdminRead(queryUserId, "admin_list_tasks", "task", null, auditDetails, httpRequest);

        Page<FileTask> taskPage;
        if (userId != null) {
            taskPage = (status != null && !status.trim().isEmpty())
                ? fileTaskRepository.findByUserIdAndStatus(userId, status, pageable)
                : fileTaskRepository.findByUserId(userId, pageable);
        } else {
            taskPage = (status != null && !status.trim().isEmpty())
                ? fileTaskRepository.findByStatus(status, pageable)
                : fileTaskRepository.findAll(pageable);
        }

        List<FileTaskVO> items = new ArrayList<>();
        for (FileTask t : taskPage.getContent()) {
            items.add(fileTaskMapper.toVO(t));
        }
        return PageResult.of(items, taskPage.getTotalElements(),
            taskPage.getNumber() + 1, taskPage.getSize());
    }

    /**
     * 获取任务详情（包含绑定标签与列信息）
     */
    public FileTaskVO getTaskDetail(Integer taskId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("taskId", taskId);
        auditService.recordAdminRead(task.getUserId(), "admin_read_task_detail", "task", taskId, auditDetails, httpRequest);

        FileTaskVO vo = fileTaskMapper.toVO(task);

        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        List<LabelVO> labelVOs = new ArrayList<>();
        for (TaskLabel tl : taskLabels) {
            labelRepository.findById(tl.getLabelId()).ifPresent(label -> labelVOs.add(labelMapper.toVO(label)));
        }
        vo.setLabels(labelVOs);

        Optional<DataRow> firstRow = dataRowRepository.findFirstByTaskIdOrderByRowIndexAsc(taskId);
        if (firstRow.isPresent() && firstRow.get().getOriginalData() != null) {
            vo.setColumns(new ArrayList<>(firstRow.get().getOriginalData().keySet()));
        } else {
            FilePreviewResult preview = fileParseService.parsePreview(task.getFilePath(), 0);
            vo.setColumns(preview.getColumns());
        }

        return vo;
    }

    /**
     * 任务归档（归档后只读）
     */
    @Transactional(rollbackFor = Exception.class)
    public void archive(Integer taskId, HttpServletRequest request) {
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
            return;
        }
        if (!TaskStatus.COMPLETED.equalsIgnoreCase(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "仅已完成任务允许归档");
        }

        task.setStatus(TaskStatus.ARCHIVED);
        task.setArchivedAt(LocalDateTime.now());
        fileTaskRepository.save(task);

        auditService.record("archive_task", "task", taskId, null, request);
    }

    /**
     * 配置任务标签（不启动分析）
     * 仅允许 uploaded 或 pending 状态的任务配置标签
     */
    @Transactional(rollbackFor = Exception.class)
    public FileTaskVO configureLabels(Integer taskId, List<Integer> labelIds, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查状态
        if (!task.canConfigureLabels()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID,
                "仅 uploaded 或 pending 状态的任务可以配置标签");
        }

        // 验证标签
        List<Label> labels = labelRepository.findByIdIn(labelIds);
        if (labels.size() != labelIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部分标签不存在");
        }

        // 验证标签归属
        for (Label label : labels) {
            if (!label.getUserId().equals(task.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用该标签: " + label.getName());
            }
        }

        // 删除旧的标签关联
        taskLabelRepository.deleteByTaskId(taskId);

        // 创建新的标签关联
        for (Label label : labels) {
            TaskLabel taskLabel = TaskLabel.builder()
                .taskId(taskId)
                .labelId(label.getId())
                .labelName(label.getName())
                .labelVersion(label.getVersion())
                .labelDescription(label.getDescription())
                .build();
            taskLabelRepository.save(taskLabel);
        }

        // 更新任务状态为 pending
        if (TaskStatus.UPLOADED.equals(task.getStatus()) && !labelIds.isEmpty()) {
            task.setStatus(TaskStatus.PENDING);
            fileTaskRepository.save(task);
        }

        // 如果移除所有标签，状态回退到 uploaded
        if (labelIds.isEmpty() && TaskStatus.PENDING.equals(task.getStatus())) {
            task.setStatus(TaskStatus.UPLOADED);
            fileTaskRepository.save(task);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("labelIds", labelIds);
        auditService.record("configure_labels", "task", taskId, details, request);

        return getTaskDetail(taskId, request);
    }

    /**
     * 移除任务标签
     * 仅允许 pending 状态的任务移除标签
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeLabel(Integer taskId, Integer labelId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查状态
        if (!TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID,
                "仅 pending 状态的任务可以移除标签");
        }

        // 删除标签关联
        taskLabelRepository.deleteByTaskIdAndLabelId(taskId, labelId);

        // 检查是否还有标签，如果没有则回退状态
        List<TaskLabel> remainingLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        if (remainingLabels.isEmpty()) {
            task.setStatus(TaskStatus.UPLOADED);
            fileTaskRepository.save(task);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("labelId", labelId);
        auditService.record("remove_label", "task", taskId, details, request);
    }

    /**
     * 启动任务分析
     * 仅允许 pending 状态的任务启动
     */
    @Transactional(rollbackFor = Exception.class)
    public FileTaskVO startTask(Integer taskId, AnalyzeTaskRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查状态
        if (!TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID,
                "仅 pending 状态的任务可以启动");
        }

        // 如果有传入标签ID，更新标签配置
        if (request != null && request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
            configureLabelsInternal(task, request.getLabelIds());
        }

        // 检查是否有标签
        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        if (taskLabels.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先配置至少一个标签");
        }

        // 保存运行时配置快照
        if (request != null) {
            if (request.getModelConfigId() != null) {
                task.setRunModelConfigName("config_" + request.getModelConfigId());
            }
            task.setRunIncludeReasoning(request.getIncludeReasoning());
        }

        // 更新状态
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        fileTaskRepository.save(task);

        Map<String, Object> details = new HashMap<>();
        details.put("labelCount", taskLabels.size());
        if (request != null) {
            details.put("modelConfigId", request.getModelConfigId());
            details.put("includeReasoning", request.getIncludeReasoning());
        }
        auditService.record("start_task", "task", taskId, details, httpRequest);

        return getTaskDetail(taskId, httpRequest);
    }

    /**
     * 继续任务（从暂停状态恢复）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileTaskVO resumeTask(Integer taskId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!TaskStatus.PAUSED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "仅暂停的任务可以继续");
        }

        task.setStatus(TaskStatus.PROCESSING);
        task.setPausedAt(null);
        fileTaskRepository.save(task);

        auditService.record("resume_task", "task", taskId, null, request);

        return getTaskDetail(taskId, request);
    }

    /**
     * 重新启动任务（用于completed/failed/cancelled状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public FileTaskVO restartTask(Integer taskId, AnalyzeTaskRequest request, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查状态：仅允许已完成、失败、取消的任务重新启动
        String status = task.getStatus();
        if (!TaskStatus.COMPLETED.equals(status) && !TaskStatus.FAILED.equals(status) && !TaskStatus.CANCELLED.equals(status)) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID,
                "仅已完成、失败或已取消的任务可以重新启动");
        }

        // 如果有传入标签ID，更新标签配置
        if (request != null && request.getLabelIds() != null && !request.getLabelIds().isEmpty()) {
            configureLabelsInternal(task, request.getLabelIds());
        }

        // 检查是否有标签
        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        if (taskLabels.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请先配置至少一个标签");
        }

        // 重置进度
        task.setProcessedRows(0);
        task.setFailedRows(0);
        task.setErrorMessage(null);

        // 保存运行时配置快照
        if (request != null) {
            if (request.getModelConfigId() != null) {
                task.setRunModelConfigName("config_" + request.getModelConfigId());
            }
            task.setRunIncludeReasoning(request.getIncludeReasoning());
        }

        // 更新状态
        task.setStatus(TaskStatus.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        fileTaskRepository.save(task);

        // 清空旧的数据行结果（可选，根据业务需求决定是否保留）
        // dataRowRepository.deleteByTaskId(taskId);

        Map<String, Object> details = new HashMap<>();
        details.put("labelCount", taskLabels.size());
        details.put("previousStatus", status);
        if (request != null) {
            details.put("modelConfigId", request.getModelConfigId());
            details.put("includeReasoning", request.getIncludeReasoning());
        }
        auditService.record("restart_task", "task", taskId, details, httpRequest);

        return getTaskDetail(taskId, httpRequest);
    }

    /**
     * 内部方法：配置标签（不检查任务状态）
     */
    private void configureLabelsInternal(FileTask task, List<Integer> labelIds) {
        Integer taskId = task.getId();

        // 验证标签
        List<Label> labels = labelRepository.findByIdIn(labelIds);
        if (labels.size() != labelIds.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部分标签不存在");
        }

        // 验证标签归属
        for (Label label : labels) {
            if (!label.getUserId().equals(task.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用该标签: " + label.getName());
            }
        }

        // 删除旧的标签关联
        taskLabelRepository.deleteByTaskId(taskId);

        // 创建新的标签关联
        for (Label label : labels) {
            TaskLabel taskLabel = TaskLabel.builder()
                .taskId(taskId)
                .labelId(label.getId())
                .labelName(label.getName())
                .labelVersion(label.getVersion())
                .labelDescription(label.getDescription())
                .build();
            taskLabelRepository.save(taskLabel);
        }
    }

    /**
     * 暂停任务
     */
    @Transactional(rollbackFor = Exception.class)
    public FileTaskVO pauseTask(Integer taskId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!TaskStatus.PROCESSING.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "仅进行中的任务可以暂停");
        }

        task.setStatus(TaskStatus.PAUSED);
        task.setPausedAt(LocalDateTime.now());
        fileTaskRepository.save(task);

        auditService.record("pause_task", "task", taskId, null, request);

        return getTaskDetail(taskId, request);
    }

    /**
     * 取消任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Integer taskId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (task.isFinished()) {
            throw new BusinessException(ErrorCode.TASK_STATUS_INVALID, "任务已结束，无法取消");
        }

        task.setStatus(TaskStatus.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        fileTaskRepository.save(task);

        auditService.record("cancel_task", "task", taskId, null, request);
    }

    /**
     * 获取数据预览（列统计）
     */
    public Map<String, Object> getTaskPreview(Integer taskId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("totalRows", task.getTotalRows());
        result.put("fileSize", task.getFileSize());

        // 获取列信息
        List<String> columns = task.getColumns();
        if (columns == null || columns.isEmpty()) {
            FilePreviewResult preview = fileParseService.parsePreview(task.getFilePath(), 0);
            columns = preview.getColumns();
        }
        result.put("totalColumns", columns != null ? columns.size() : 0);

        // 构建列详情
        List<Map<String, Object>> columnDetails = new ArrayList<>();
        if (columns != null) {
            for (int i = 0; i < columns.size(); i++) {
                Map<String, Object> col = new HashMap<>();
                col.put("index", i);
                col.put("name", columns.get(i));
                col.put("dataType", "文本"); // 简化处理
                col.put("nonNullRate", 100); // 简化处理
                columnDetails.add(col);
            }
        }
        result.put("columns", columnDetails);

        // 获取预览数据
        FilePreviewResult preview = fileParseService.parsePreview(task.getFilePath(), 10);
        result.put("previewRows", preview.getPreview());

        return result;
    }

    /**
     * 创建任务临时标签
     * 临时标签仅在当前任务中可见，可用于分析数据行
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO createTempLabel(Integer taskId, String name, String description,
                                   List<String> focusColumns, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查同名临时标签是否已存在
        List<Label> existingLabels = labelRepository.findByTaskIdAndScope(taskId, Label.Scope.TASK);
        for (Label existing : existingLabels) {
            if (existing.getName().equals(name)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "同名临时标签已存在: " + name);
            }
        }

        // 创建临时标签
        Label label = Label.builder()
            .userId(task.getUserId())
            .name(name)
            .description(description)
            .focusColumns(focusColumns)
            .scope(Label.Scope.TASK)
            .taskId(taskId)
            .version(1)
            .build();
        label = labelRepository.save(label);

        // 自动关联到任务
        TaskLabel taskLabel = TaskLabel.builder()
            .taskId(taskId)
            .labelId(label.getId())
            .labelName(label.getName())
            .labelVersion(label.getVersion())
            .labelDescription(label.getDescription())
            .build();
        taskLabelRepository.save(taskLabel);

        Map<String, Object> details = new HashMap<>();
        details.put("labelId", label.getId());
        details.put("labelName", name);
        details.put("scope", Label.Scope.TASK);
        auditService.record("create_temp_label", "label", label.getId(), details, request);

        return labelMapper.toVO(label);
    }

    /**
     * 将临时标签保存到全局标签库
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO promoteTempLabelToGlobal(Integer taskId, Integer labelId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "标签不存在"));

        // 验证标签属于该任务
        if (!Label.Scope.TASK.equals(label.getScope()) || !taskId.equals(label.getTaskId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该标签不是此任务的临时标签");
        }

        // 检查全局标签库中是否已有同名标签
        List<Label> globalLabels = labelRepository.findByUserIdAndScope(task.getUserId(), Label.Scope.GLOBAL);
        for (Label existing : globalLabels) {
            if (existing.getName().equals(label.getName())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "全局标签库中已存在同名标签: " + label.getName());
            }
        }

        // 提升为全局标签
        label.promoteToGlobal();
        label = labelRepository.save(label);

        Map<String, Object> details = new HashMap<>();
        details.put("labelId", labelId);
        details.put("labelName", label.getName());
        details.put("fromTaskId", taskId);
        auditService.record("promote_temp_label", "label", labelId, details, request);

        return labelMapper.toVO(label);
    }

    /**
     * 获取任务的临时标签列表
     */
    public List<LabelVO> getTempLabels(Integer taskId, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Label> tempLabels = labelRepository.findByTaskIdAndScope(taskId, Label.Scope.TASK);
        return tempLabels.stream()
            .map(labelMapper::toVO)
            .collect(Collectors.toList());
    }

    /**
     * 更新单行信心度阈值
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRowThreshold(Integer taskId, Long rowId, BigDecimal threshold, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        DataRow row = dataRowRepository.findById(rowId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据行不存在"));
        
        if (!row.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "数据行不属于该任务");
        }

        row.setConfidenceThreshold(threshold);
        row.updateNeedsReview();
        dataRowRepository.save(row);

        Map<String, Object> details = new HashMap<>();
        details.put("rowId", rowId);
        details.put("threshold", threshold);
        auditService.record("update_row_threshold", "data_row", rowId.intValue(), details, request);
    }

    private String calculateSha256(String filePath) {
        try (InputStream in = Files.newInputStream(Paths.get(filePath))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                digest.update(buffer, 0, n);
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            log.warn("计算文件哈希失败: {} - {}", filePath, e.getMessage());
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
