package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.response.TaskExecutionLogVO;
import com.datalabeling.service.ExecutionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 任务执行日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/execution-logs")
@RequiredArgsConstructor
public class ExecutionLogController {

    private final ExecutionLogService executionLogService;

    /**
     * 获取任务执行日志（分页）
     *
     * @param analysisTaskId 任务ID（必需）
     * @param logLevel       日志级别（可选）
     * @param dataRowId      数据行ID（可选）
     * @param page           页码（默认1）
     * @param size           每页数量（默认50）
     * @return 分页日志结果
     */
    @GetMapping
    public ApiResponse<PageResult<TaskExecutionLogVO>> listExecutionLogs(
            @RequestParam Integer analysisTaskId,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) Long dataRowId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {

        log.debug("查询执行日志: taskId={}, logLevel={}, dataRowId={}, page={}, size={}",
                analysisTaskId, logLevel, dataRowId, page, size);

        return ApiResponse.success(executionLogService.listExecutionLogs(
                analysisTaskId, logLevel, dataRowId, page, size));
    }
}
