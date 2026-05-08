package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.UpdateLabelResultRequest;
import com.datalabeling.dto.response.LabelResultVO;
import com.datalabeling.service.LabelResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 标签结果控制器
 */
@RestController
@RequestMapping("/label-results")
@RequiredArgsConstructor
public class LabelResultController {
    
    private final LabelResultService labelResultService;
    
    /**
     * 获取任务的标签结果列表
     */
    @GetMapping
    public ApiResponse<PageResult<LabelResultVO>> getResults(
            @RequestParam Integer taskId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestParam(required = false) Integer labelId,
            @RequestParam(required = false) String resultValue,
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) Boolean isModified) {
        return ApiResponse.success(labelResultService.getResults(taskId, page, size, labelId, resultValue, needsReview, isModified));
    }
    
    /**
     * 获取单个标签结果详情
     */
    @GetMapping("/{id}")
    public ApiResponse<LabelResultVO> getResult(@PathVariable Long id) {
        return ApiResponse.success(labelResultService.getResult(id));
    }
    
    /**
     * 更新标签结果（人工修改）
     */
    @PutMapping("/{id}")
    public ApiResponse<LabelResultVO> updateResult(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLabelResultRequest request) {
        return ApiResponse.success(labelResultService.updateResult(id, request));
    }
    
    /**
     * 批量更新信心度阈值
     */
    @PostMapping("/batch-threshold")
    public ApiResponse<Void> batchUpdateThreshold(
            @RequestParam Integer taskId,
            @RequestParam(required = false) Integer labelId,
            @RequestParam BigDecimal threshold) {
        labelResultService.batchUpdateThreshold(taskId, labelId, threshold);
        return ApiResponse.success(null);
    }
    
    /**
     * 获取任务的标签统计
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getTaskStatistics(@RequestParam Integer taskId) {
        return ApiResponse.success(labelResultService.getTaskStatistics(taskId));
    }

    /**
     * 按数据行分页获取标签结果
     * 解决原接口按LabelResult分页导致数据行不完整的问题
     */
    @GetMapping("/by-row")
    public ApiResponse<PageResult<Map<String, Object>>> getResultsByDataRow(
            @RequestParam Integer taskId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String resultFilter,
            @RequestParam(required = false) Boolean onlyNeedsReview) {
        return ApiResponse.success(labelResultService.getResultsByDataRow(taskId, page, size, resultFilter, onlyNeedsReview));
    }
}