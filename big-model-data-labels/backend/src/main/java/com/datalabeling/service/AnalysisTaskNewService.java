package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.AnalysisTaskMapper;
import com.datalabeling.dto.request.CreateAnalysisTaskRequest;
import com.datalabeling.dto.response.AnalysisTaskVO;
import com.datalabeling.dto.response.AnalysisProcessVO;
import com.datalabeling.entity.*;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.*;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析任务服务（新版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskNewService {
    
    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisTaskLabelRepository analysisTaskLabelRepository;
    private final DatasetRepository datasetRepository;
    private final DataRowRepository dataRowRepository;
    private final LabelRepository labelRepository;
    private final LabelResultRepository labelResultRepository;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final SecurityUtil securityUtil;
    private final AnalysisTaskAsyncService analysisTaskAsyncService;
    
    /**
     * 获取分析任务列表
     * 如果指定datasetId，则返回该数据集的任务；否则返回当前用户的所有任务
     */
    public PageResult<AnalysisTaskVO> getTasks(Integer datasetId, String status, Integer page, Integer size) {
        Integer userId = securityUtil.getCurrentUserId();
        
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AnalysisTask> taskPage;
        
        if (datasetId != null) {
            // 验证数据集归属
            datasetRepository.findByIdAndUserId(datasetId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
            
            taskPage = analysisTaskRepository.findByDatasetId(datasetId, pageable);
        } else {
            // 查询当前用户的所有任务
            if (status != null && !status.isEmpty()) {
                taskPage = analysisTaskRepository.findByUserIdAndStatus(userId, status, pageable);
            } else {
                taskPage = analysisTaskRepository.findByUserId(userId, pageable);
            }
        }
        
        List<AnalysisTaskVO> voList = taskPage.getContent().stream()
                .map(this::enrichTaskVO)
                .collect(Collectors.toList());
        
        return PageResult.of(voList, taskPage.getTotalElements(), page, size);
    }
    
    /**
     * 获取数据集的分析任务列表（保留兼容性）
     */
    public PageResult<AnalysisTaskVO> getTasksByDataset(Integer datasetId, Integer page, Integer size) {
        return getTasks(datasetId, null, page, size);
    }
    
    /**
     * 获取分析任务详情
     */
    public AnalysisTaskVO getTask(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        return enrichTaskVO(task);
    }
    
    /**
     * 创建分析任务
     */
    @Transactional
    public AnalysisTaskVO createTask(CreateAnalysisTaskRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        
        // 验证数据集
        Dataset dataset = datasetRepository.findByIdAndUserId(request.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 验证标签
        List<Label> labels = labelRepository.findByIdIn(request.getLabelIds());
        if (labels.size() != request.getLabelIds().size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "部分标签不存在");
        }
        
        // 验证标签可用性（全局标签或数据集专属标签）
        for (Label label : labels) {
            if (!label.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权使用该标签");
            }
            if (label.isDatasetSpecific() && !label.getDatasetId().equals(request.getDatasetId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "标签 " + label.getName() + " 不属于该数据集");
            }
        }
        
        // 创建任务
        String taskName = request.getName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = dataset.getOriginalFilename() + "_" + System.currentTimeMillis();
        }
        
        BigDecimal threshold = request.getDefaultConfidenceThreshold();
        if (threshold == null) {
            threshold = new BigDecimal("0.80");
        }

        // 并发数，默认为1
        Integer concurrency = request.getConcurrency();
        if (concurrency == null || concurrency < 1) {
            concurrency = 1;
        } else if (concurrency > 10) {
            concurrency = 10;
        }

        AnalysisTask task = AnalysisTask.builder()
                .datasetId(request.getDatasetId())
                .userId(userId)
                .name(taskName)
                .description(request.getDescription())
                .status(AnalysisTask.Status.PENDING)
                .totalRows(dataset.getTotalRows())
                .processedRows(0)
                .successRows(0)
                .failedRows(0)
                .defaultConfidenceThreshold(threshold)
                .modelConfigId(request.getModelConfigId())
                .concurrency(concurrency)
                .build();
        
        task = analysisTaskRepository.save(task);
        
        // 创建任务-标签关联（保存标签快照）
        for (Label label : labels) {
            AnalysisTaskLabel taskLabel = AnalysisTaskLabel.builder()
                    .analysisTaskId(task.getId())
                    .labelId(label.getId())
                    .labelName(label.getName())
                    .labelVersion(label.getVersion())
                    .labelDescription(label.getDescription())
                    .build();
            analysisTaskLabelRepository.save(taskLabel);
        }
        
        log.info("用户 {} 创建分析任务: {}, 数据集: {}, 标签数: {}", 
                userId, task.getName(), dataset.getOriginalFilename(), labels.size());
        
        // 如果需要立即开始
        if (Boolean.TRUE.equals(request.getAutoStart())) {
            startTask(task.getId());
        }
        
        return enrichTaskVO(task);
    }
    
    /**
     * 启动分析任务
     */
    @Transactional
    public AnalysisTaskVO startTask(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 检查状态
        if (!AnalysisTask.Status.PENDING.equals(task.getStatus()) && 
            !AnalysisTask.Status.PAUSED.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "任务状态不允许启动");
        }
        
        // 更新状态
        task.setStatus(AnalysisTask.Status.PROCESSING);
        task.setStartedAt(LocalDateTime.now());
        task = analysisTaskRepository.save(task);
        
        // 记录日志
        TaskExecutionLog logEntry = TaskExecutionLog.taskLog(taskId, TaskExecutionLog.LogLevel.INFO, "任务开始执行");
        taskExecutionLogRepository.save(logEntry);
        
        log.info("分析任务 {} 开始执行", taskId);

        // 在事务提交后触发异步执行，确保状态已持久化
        final Integer finalTaskId = taskId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("事务已提交，触发异步执行任务: {}", finalTaskId);
                analysisTaskAsyncService.executeAsync(finalTaskId);
            }
        });

        return enrichTaskVO(task);
    }
    
    /**
     * 暂停分析任务
     */
    @Transactional
    public AnalysisTaskVO pauseTask(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 检查状态
        if (!AnalysisTask.Status.PROCESSING.equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "任务状态不允许暂停");
        }
        
        // 更新状态
        task.setStatus(AnalysisTask.Status.PAUSED);
        task = analysisTaskRepository.save(task);
        
        // 记录日志
        TaskExecutionLog logEntry = TaskExecutionLog.taskLog(taskId, TaskExecutionLog.LogLevel.INFO, "任务已暂停");
        taskExecutionLogRepository.save(logEntry);
        
        log.info("分析任务 {} 已暂停", taskId);
        
        return enrichTaskVO(task);
    }
    
    /**
     * 取消分析任务
     */
    @Transactional
    public void cancelTask(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 检查状态
        if (task.isFinished()) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "任务已结束，无法取消");
        }
        
        // 更新状态
        task.setStatus(AnalysisTask.Status.CANCELLED);
        task.setCompletedAt(LocalDateTime.now());
        analysisTaskRepository.save(task);
        
        // 记录日志
        TaskExecutionLog logEntry = TaskExecutionLog.taskLog(taskId, TaskExecutionLog.LogLevel.INFO, "任务已取消");
        taskExecutionLogRepository.save(logEntry);
        
        log.info("分析任务 {} 已取消", taskId);
    }
    
    /**
     * 删除分析任务
     */
    @Transactional
    public void deleteTask(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 检查状态
        if (task.isProcessing()) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "任务正在执行中，请先暂停或取消");
        }
        
        // 删除关联数据
        labelResultRepository.deleteByAnalysisTaskId(taskId);
        taskExecutionLogRepository.deleteByAnalysisTaskId(taskId);
        analysisTaskLabelRepository.deleteByAnalysisTaskId(taskId);
        
        // 删除任务
        analysisTaskRepository.delete(task);
        
        log.info("分析任务 {} 已删除", taskId);
    }
    
    /**
     * 获取任务执行日志
     */
    public PageResult<TaskExecutionLog> getTaskLogs(Integer taskId, Integer page, Integer size, String level) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TaskExecutionLog> logPage;
        
        if (level != null && !level.isEmpty()) {
            logPage = taskExecutionLogRepository.findByAnalysisTaskIdAndLogLevel(taskId, level, pageable);
        } else {
            logPage = taskExecutionLogRepository.findByAnalysisTaskId(taskId, pageable);
        }
        
        return PageResult.of(logPage.getContent(), logPage.getTotalElements(), page, size);
    }
    
    /**
     * 获取增量日志（用于实时更新）
     */
    public List<TaskExecutionLog> getLogsSince(Integer taskId, LocalDateTime since, Integer limit) {
        Integer userId = securityUtil.getCurrentUserId();

        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));

        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        List<TaskExecutionLog> logs = taskExecutionLogRepository.findLogsSince(taskId, since);

        // 限制返回数量
        if (logs.size() > limit) {
            return logs.subList(0, limit);
        }
        return logs;
    }

    /**
     * 获取任务进度（用于前端轮询）
     */
    public com.datalabeling.dto.response.AnalysisTaskProgressVO getTaskProgress(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();

        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));

        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 计算进度百分比
        int percentage = task.getTotalRows() > 0
                ? (int) Math.round((double) task.getProcessedRows() / task.getTotalRows() * 100)
                : 0;

        // 计算预计剩余时间
        Integer etaSeconds = null;
        if (task.getStartedAt() != null && task.getProcessedRows() > 0) {
            long elapsedSeconds = Duration.between(task.getStartedAt(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds > 0) {
                double rowsPerSecond = (double) task.getProcessedRows() / elapsedSeconds;
                if (rowsPerSecond > 0) {
                    int remaining = task.getTotalRows() - task.getProcessedRows();
                    etaSeconds = (int) Math.ceil(remaining / rowsPerSecond);
                }
            }
        }

        // 获取任务标签
        List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(taskId);

        // 构建分标签进度
        List<com.datalabeling.dto.response.AnalysisTaskProgressVO.LabelProgressItem> labelProgress =
                taskLabels.stream()
                        .map(tl -> {
                            int labelPercentage = task.getTotalRows() > 0
                                    ? (int) Math.round((double) task.getProcessedRows() / task.getTotalRows() * 100)
                                    : 0;
                            return com.datalabeling.dto.response.AnalysisTaskProgressVO.LabelProgressItem.builder()
                                    .labelId(tl.getLabelId())
                                    .labelName(tl.getLabelName())
                                    .processed(task.getProcessedRows())
                                    .total(task.getTotalRows())
                                    .percentage(labelPercentage)
                                    .build();
                        })
                        .collect(Collectors.toList());

        return com.datalabeling.dto.response.AnalysisTaskProgressVO.builder()
                .taskId(taskId)
                .status(task.getStatus())
                .total(task.getTotalRows())
                .processed(task.getProcessedRows())
                .success(task.getSuccessRows())
                .failed(task.getFailedRows())
                .percentage(percentage)
                .etaSeconds(etaSeconds)
                .labelProgress(labelProgress)
                .build();
    }

    /**
     * 获取分析过程详情（包含标签列表和最新日志）
     */
    public AnalysisProcessVO getAnalysisProcess(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        
        // 验证数据集归属
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 获取任务标签
        List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(taskId);
        
        // 获取命中统计
        Map<Integer, Long> hitCountMap = new HashMap<>();
        List<Object[]> hitCounts = labelResultRepository.countHitsByLabelId(taskId);
        for (Object[] row : hitCounts) {
            Integer labelId = (Integer) row[0];
            Long count = (Long) row[1];
            hitCountMap.put(labelId, count);
        }
        
        // 构建标签列表
        List<AnalysisProcessVO.AnalyzingLabel> analyzingLabels = taskLabels.stream()
                .map(tl -> {
                    Long hitCount = hitCountMap.getOrDefault(tl.getLabelId(), 0L);
                    double hitRate = task.getProcessedRows() > 0
                            ? (double) hitCount / task.getProcessedRows() * 100
                            : 0.0;
                    
                    return AnalysisProcessVO.AnalyzingLabel.builder()
                            .labelId(tl.getLabelId())
                            .labelName(tl.getLabelName())
                            .labelVersion(tl.getLabelVersion())
                            .labelDescription(tl.getLabelDescription())
                            .processedRows(task.getProcessedRows())
                            .hitCount(hitCount)
                            .hitRate(Math.round(hitRate * 100.0) / 100.0)
                            .isProcessing(AnalysisTask.Status.PROCESSING.equals(task.getStatus()))
                            .processingStatus(task.getStatus())
                            .build();
                })
                .collect(Collectors.toList());
        
        // 获取最新日志
        Pageable logPageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TaskExecutionLog> recentLogEntities = taskExecutionLogRepository
                .findRecentLogsByAnalysisTaskId(taskId, logPageable);
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        List<AnalysisProcessVO.AnalysisLogEntry> recentLogs = recentLogEntities.stream()
                .map(log -> AnalysisProcessVO.AnalysisLogEntry.builder()
                        .id(log.getId())
                        .dataRowId(log.getDataRowId())
                        .rowIndex(log.getRowIndex())
                        .labelKey(log.getLabelKey())
                        .logLevel(log.getLogLevel())
                        .message(log.getMessage())
                        .confidence(log.getConfidence())
                        .durationMs(log.getDurationMs())
                        .createdAt(log.getCreatedAt())
                        .timeDisplay(log.getCreatedAt() != null ? log.getCreatedAt().format(timeFormatter) : "")
                        .build())
                .collect(Collectors.toList());
        
        // 计算预计剩余时间
        Integer estimatedSeconds = null;
        if (task.getStartedAt() != null && task.getProcessedRows() > 0) {
            long elapsedSeconds = Duration.between(task.getStartedAt(), LocalDateTime.now()).getSeconds();
            if (elapsedSeconds > 0) {
                double rowsPerSecond = (double) task.getProcessedRows() / elapsedSeconds;
                if (rowsPerSecond > 0) {
                    int remaining = task.getTotalRows() - task.getProcessedRows();
                    estimatedSeconds = (int) Math.ceil(remaining / rowsPerSecond);
                }
            }
        }
        
        // 计算进度百分比
        int progressPercent = task.getTotalRows() > 0
                ? (int) Math.round((double) task.getProcessedRows() / task.getTotalRows() * 100)
                : 0;
        
        // 构建当前处理信息
        AnalysisProcessVO.CurrentProcessingInfo currentProcessing = null;
        if (AnalysisTask.Status.PROCESSING.equals(task.getStatus()) && !recentLogEntities.isEmpty()) {
            TaskExecutionLog latestLog = recentLogEntities.get(0);
            currentProcessing = AnalysisProcessVO.CurrentProcessingInfo.builder()
                    .currentRowIndex(latestLog.getRowIndex())
                    .currentLabelName(latestLog.getLabelKey())
                    .processingPhase("processing")
                    .processingPhaseDisplay("正在分析")
                    .build();
        }
        
        return AnalysisProcessVO.builder()
                .taskId(taskId)
                .taskName(task.getName())
                .status(task.getStatus())
                .statusDisplay(getStatusDisplayString(task.getStatus()))
                .totalRows(task.getTotalRows())
                .processedRows(task.getProcessedRows())
                .successRows(task.getSuccessRows())
                .failedRows(task.getFailedRows())
                .progressPercent(progressPercent)
                .estimatedSecondsRemaining(estimatedSeconds)
                .analyzingLabels(analyzingLabels)
                .recentLogs(recentLogs)
                .currentProcessing(currentProcessing)
                .startedAt(task.getStartedAt())
                .lastUpdatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 获取状态显示名称
     */
    private String getStatusDisplayString(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case AnalysisTask.Status.PENDING:
                return "待启动";
            case AnalysisTask.Status.PROCESSING:
                return "进行中";
            case AnalysisTask.Status.PAUSED:
                return "已暂停";
            case AnalysisTask.Status.COMPLETED:
                return "已完成";
            case AnalysisTask.Status.FAILED:
                return "失败";
            case AnalysisTask.Status.CANCELLED:
                return "已取消";
            default:
                return status;
        }
    }
    
    /**
     * 丰富任务VO（添加统计信息）
     */
    private AnalysisTaskVO enrichTaskVO(AnalysisTask task) {
        // 获取数据集名称
        String datasetName = datasetRepository.findById(task.getDatasetId())
                .map(Dataset::getOriginalFilename)
                .orElse(null);

        // 获取任务标签
        List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(task.getId());

        // 获取标签详情
        List<Integer> labelIds = taskLabels.stream()
                .map(AnalysisTaskLabel::getLabelId)
                .collect(Collectors.toList());
        Map<Integer, Label> labelMap = labelRepository.findByIdIn(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, l -> l));

        // 获取命中统计
        Map<Integer, Long> hitCountMap = new HashMap<>();
        List<Object[]> hitCounts = labelResultRepository.countHitsByLabelId(task.getId());
        for (Object[] row : hitCounts) {
            Integer labelId = (Integer) row[0];
            Long count = (Long) row[1];
            hitCountMap.put(labelId, count);
        }

        // 获取审核统计
        Long needsReviewCount = labelResultRepository.countByAnalysisTaskIdAndNeedsReviewTrue(task.getId());
        Long modifiedCount = labelResultRepository.countByAnalysisTaskIdAndIsModifiedTrue(task.getId());

        return analysisTaskMapper.toVO(task, datasetName, taskLabels, labelMap, hitCountMap, needsReviewCount, modifiedCount);
    }

    /**
     * 获取导出所需的任务结果数据
     * 返回包含数据集信息、标签列和所有数据行结果
     * @param taskId 任务ID
     * @param includeReasoning 是否包含判断依据（合并到结果单元格中）
     */
    public Map<String, Object> getExportData(Integer taskId, Boolean includeReasoning) {
        Integer userId = securityUtil.getCurrentUserId();

        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));

        // 验证数据集归属
        Dataset dataset = datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 获取任务标签
        List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(taskId);

        // 获取所有标签结果
        List<LabelResult> allResults = labelResultRepository.findByAnalysisTaskId(taskId);

        // 获取所有数据行
        List<Long> dataRowIds = allResults.stream()
                .map(LabelResult::getDataRowId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, DataRow> dataRowMap = dataRowRepository.findAllById(dataRowIds).stream()
                .collect(Collectors.toMap(DataRow::getId, dr -> dr));

        // 按数据行分组标签结果
        Map<Long, List<LabelResult>> resultsByRow = allResults.stream()
                .collect(Collectors.groupingBy(LabelResult::getDataRowId));

        // 构建导出数据
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("taskName", task.getName());
        exportData.put("datasetName", dataset.getOriginalFilename());

        // 原始数据列名
        List<String> originalColumns = new ArrayList<>();
        if (dataset.getColumns() != null) {
            for (Map<String, Object> col : dataset.getColumns()) {
                String name = (String) col.get("name");
                if (name != null) {
                    originalColumns.add(name);
                }
            }
        }
        exportData.put("originalColumns", originalColumns);

        // 标签列（名称_版本号）
        List<String> labelColumns = taskLabels.stream()
                .map(tl -> tl.getLabelName() + "_v" + tl.getLabelVersion())
                .collect(Collectors.toList());
        exportData.put("labelColumns", labelColumns);

        // 构建数据行
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long dataRowId : dataRowIds) {
            DataRow dataRow = dataRowMap.get(dataRowId);
            if (dataRow == null) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("rowIndex", dataRow.getRowIndex());
            row.put("originalData", dataRow.getOriginalData());

            // 标签结果
            Map<String, String> labelResultsMap = new HashMap<>();
            List<LabelResult> rowResults = resultsByRow.getOrDefault(dataRowId, Collections.emptyList());
            for (LabelResult lr : rowResults) {
                AnalysisTaskLabel taskLabel = taskLabels.stream()
                        .filter(tl -> tl.getLabelId().equals(lr.getLabelId()))
                        .findFirst().orElse(null);
                if (taskLabel != null) {
                    String key = taskLabel.getLabelName() + "_v" + taskLabel.getLabelVersion();
                    // 如果需要包含判断依据，则将结果和原因合并到一个单元格
                    String cellValue;
                    if (Boolean.TRUE.equals(includeReasoning) && lr.getAiReasoning() != null && !lr.getAiReasoning().isEmpty()) {
                        // 格式：结论：xxx\n判断依据：xxx
                        cellValue = "结论：" + lr.getResult() + "\n判断依据：" + lr.getAiReasoning();
                    } else {
                        cellValue = lr.getResult();
                    }
                    labelResultsMap.put(key, cellValue);
                }
            }
            row.put("labelResults", labelResultsMap);

            rows.add(row);
        }

        // 按行索引排序
        rows.sort((a, b) -> {
            Integer indexA = (Integer) a.get("rowIndex");
            Integer indexB = (Integer) b.get("rowIndex");
            return (indexA != null ? indexA : 0) - (indexB != null ? indexB : 0);
        });

        exportData.put("rows", rows);

        log.info("准备导出任务 {} 的结果, 共 {} 行, 包含判断依据: {}", taskId, rows.size(), includeReasoning);

        return exportData;
    }
}