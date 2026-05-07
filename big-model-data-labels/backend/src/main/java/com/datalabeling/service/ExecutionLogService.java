package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.response.TaskExecutionLogVO;
import com.datalabeling.entity.AnalysisTask;
import com.datalabeling.entity.TaskExecutionLog;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.AnalysisTaskRepository;
import com.datalabeling.repository.DatasetRepository;
import com.datalabeling.repository.TaskExecutionLogRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务执行日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionLogService {

    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final DatasetRepository datasetRepository;
    private final SecurityUtil securityUtil;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 获取任务执行日志（分页）
     *
     * @param analysisTaskId 任务ID
     * @param logLevel       日志级别（可选）
     * @param dataRowId      数据行ID（可选）
     * @param page           页码
     * @param size           每页数量
     * @return 分页结果
     */
    public PageResult<TaskExecutionLogVO> listExecutionLogs(
            Integer analysisTaskId,
            String logLevel,
            Long dataRowId,
            Integer page,
            Integer size) {

        Integer userId = securityUtil.getCurrentUserId();

        // 验证任务存在并归属当前用户
        AnalysisTask task = analysisTaskRepository.findById(analysisTaskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));

        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "无权访问该任务"));

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TaskExecutionLog> logPage;

        if (logLevel != null && !logLevel.isEmpty()) {
            // 按日志级别过滤
            logPage = taskExecutionLogRepository.findByAnalysisTaskIdAndLogLevel(
                    analysisTaskId, logLevel, pageable);
        } else if (dataRowId != null) {
            // 按数据行ID过滤（需要手动查询）
            List<TaskExecutionLog> filteredLogs = taskExecutionLogRepository
                    .findByAnalysisTaskIdAndDataRowId(analysisTaskId, dataRowId);
            logPage = createPage(filteredLogs, pageable);
        } else {
            // 无过滤条件
            logPage = taskExecutionLogRepository.findByAnalysisTaskId(analysisTaskId, pageable);
        }

        List<TaskExecutionLogVO> voList = logPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, logPage.getTotalElements(), page, size);
    }

    /**
     * 将实体转换为VO
     */
    private TaskExecutionLogVO convertToVO(TaskExecutionLog log) {
        return TaskExecutionLogVO.builder()
                .id(log.getId())
                .taskId(log.getAnalysisTaskId())
                .rowId(log.getDataRowId())
                .rowNumber(log.getRowIndex())
                .logLevel(log.getLogLevel())
                .logLevelIcon(TaskExecutionLogVO.getLogLevelIcon(log.getLogLevel()))
                .message(log.getMessage())
                .details(log.getLabelKey() != null ? "标签: " + log.getLabelKey() : null)
                .createdAt(log.getCreatedAt())
                .timeDisplay(log.getCreatedAt() != null
                        ? log.getCreatedAt().format(TIME_FORMATTER) : "")
                .build();
    }

    /**
     * 从列表创建分页对象
     */
    private <T> Page<T> createPage(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());

        if (start >= list.size()) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(),
                    pageable, list.size());
        }

        return new org.springframework.data.domain.PageImpl<>(list.subList(start, end),
                pageable, list.size());
    }
}
