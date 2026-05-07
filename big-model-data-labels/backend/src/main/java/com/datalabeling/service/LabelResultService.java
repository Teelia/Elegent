package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.LabelResultMapper;
import com.datalabeling.dto.request.UpdateLabelResultRequest;
import com.datalabeling.dto.response.LabelResultVO;
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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签结果服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelResultService {
    
    private final LabelResultRepository labelResultRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisTaskLabelRepository analysisTaskLabelRepository;
    private final DatasetRepository datasetRepository;
    private final DataRowRepository dataRowRepository;
    private final LabelRepository labelRepository;
    private final LabelResultMapper labelResultMapper;
    private final SecurityUtil securityUtil;
    
    /**
     * 获取任务的标签结果列表（分页）
     */
    public PageResult<LabelResultVO> getResults(Integer taskId, Integer page, Integer size, 
                                                 Integer labelId, String resultValue, 
                                                 Boolean needsReview, Boolean isModified) {
        Integer userId = securityUtil.getCurrentUserId();
        
        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "dataRowId"));
        Page<LabelResult> resultPage;
        
        // 根据筛选条件查询
        if (needsReview != null && needsReview) {
            if (labelId != null) {
                resultPage = labelResultRepository.findByAnalysisTaskIdAndLabelIdAndNeedsReviewTrue(taskId, labelId, pageable);
            } else {
                resultPage = labelResultRepository.findByAnalysisTaskIdAndNeedsReviewTrue(taskId, pageable);
            }
        } else if (isModified != null && isModified) {
            resultPage = labelResultRepository.findByAnalysisTaskIdAndIsModifiedTrue(taskId, pageable);
        } else if (labelId != null && resultValue != null) {
            resultPage = labelResultRepository.findByAnalysisTaskIdAndLabelIdAndResult(taskId, labelId, resultValue, pageable);
        } else if (labelId != null) {
            resultPage = labelResultRepository.findByAnalysisTaskIdAndLabelId(taskId, labelId, pageable);
        } else if (resultValue != null) {
            resultPage = labelResultRepository.findByAnalysisTaskIdAndResult(taskId, resultValue, pageable);
        } else {
            resultPage = labelResultRepository.findByAnalysisTaskId(taskId, pageable);
        }
        
        // 获取标签名称映射
        List<Integer> labelIds = resultPage.getContent().stream()
                .map(LabelResult::getLabelId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, String> labelNameMap = labelRepository.findByIdIn(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, Label::getName));

        // 获取数据行信息
        List<Long> dataRowIds = resultPage.getContent().stream()
                .map(LabelResult::getDataRowId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, DataRow> dataRowMap = dataRowRepository.findAllById(dataRowIds).stream()
                .collect(Collectors.toMap(DataRow::getId, dr -> dr));

        // 转换为VO（包含原始数据）
        List<LabelResultVO> voList = resultPage.getContent().stream()
                .map(r -> {
                    DataRow dataRow = dataRowMap.get(r.getDataRowId());
                    Integer rowIndex = dataRow != null ? dataRow.getRowIndex() : null;
                    Map<String, Object> originalData = dataRow != null ? dataRow.getOriginalData() : null;
                    return labelResultMapper.toVO(r, labelNameMap.get(r.getLabelId()), rowIndex, null, originalData);
                })
                .collect(Collectors.toList());
        
        return PageResult.of(voList, resultPage.getTotalElements(), page, size);
    }
    
    /**
     * 获取单个标签结果详情
     */
    public LabelResultVO getResult(Long resultId) {
        Integer userId = securityUtil.getCurrentUserId();
        
        LabelResult result = labelResultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "标签结果不存在"));
        
        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(result.getAnalysisTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        // 获取标签名称
        String labelName = labelRepository.findById(result.getLabelId())
                .map(Label::getName)
                .orElse(null);
        
        // 获取数据行信息
        DataRow dataRow = dataRowRepository.findById(result.getDataRowId()).orElse(null);
        Integer rowNumber = dataRow != null ? dataRow.getRowIndex() : null;
        Map<String, Object> rowData = dataRow != null ? dataRow.getOriginalData() : null;
        
        // 获取原始内容（关注列的值）
        String originalContent = null;
        if (rowData != null) {
            Label label = labelRepository.findById(result.getLabelId()).orElse(null);
            if (label != null && label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
                String focusColumn = label.getFocusColumns().get(0);
                Object value = rowData.get(focusColumn);
                originalContent = value != null ? value.toString() : null;
            }
        }
        
        return labelResultMapper.toVO(result, labelName, rowNumber, originalContent, rowData);
    }
    
    /**
     * 更新标签结果（人工修改）
     */
    @Transactional
    public LabelResultVO updateResult(Long resultId, UpdateLabelResultRequest request) {
        Integer userId = securityUtil.getCurrentUserId();

        LabelResult result = labelResultRepository.findById(resultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "标签结果不存在"));

        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(result.getAnalysisTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 更新结果值（兼容两种字段名）
        String actualResult = request.getActualResult();
        if (actualResult != null) {
            result.setResult(actualResult);
            result.setIsModified(true);
        }

        // 更新信心度阈值
        if (request.getConfidenceThreshold() != null) {
            result.setConfidenceThreshold(request.getConfidenceThreshold());
            result.updateNeedsReview();
        }

        // 更新提取数据（仅提取类型标签）
        if (request.getExtractedData() != null && Label.Type.EXTRACTION.equals(result.getLabelType())) {
            try {
                String extractedDataJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(request.getExtractedData());
                result.setExtractedData(extractedDataJson);
                result.setIsModified(true);
            } catch (Exception e) {
                log.warn("序列化提取数据失败: {}", e.getMessage());
            }
        }

        result = labelResultRepository.save(result);

        log.info("用户 {} 更新标签结果 {}: 结果={}, 阈值={}, 提取数据={}",
                userId, resultId, actualResult, request.getConfidenceThreshold(),
                request.getExtractedData() != null);

        // 获取标签名称
        String labelName = labelRepository.findById(result.getLabelId())
                .map(Label::getName)
                .orElse(null);

        return labelResultMapper.toVO(result, labelName);
    }
    
    /**
     * 批量更新信心度阈值
     */
    @Transactional
    public void batchUpdateThreshold(Integer taskId, Integer labelId, BigDecimal threshold) {
        Integer userId = securityUtil.getCurrentUserId();
        
        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));
        
        int updated;
        if (labelId != null) {
            updated = labelResultRepository.updateConfidenceThresholdByAnalysisTaskIdAndLabelId(taskId, labelId, threshold);
        } else {
            updated = labelResultRepository.updateConfidenceThresholdByAnalysisTaskId(taskId, threshold);
        }
        
        log.info("用户 {} 批量更新任务 {} 的信心度阈值为 {}, 影响 {} 条记录", userId, taskId, threshold, updated);
    }
    
    /**
     * 获取任务的标签统计
     */
    public Map<String, Object> getTaskStatistics(Integer taskId) {
        Integer userId = securityUtil.getCurrentUserId();

        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        Map<String, Object> statistics = new HashMap<>();

        // 获取任务标签
        List<AnalysisTaskLabel> taskLabels = analysisTaskLabelRepository.findByAnalysisTaskId(taskId);

        List<Map<String, Object>> labelStats = new ArrayList<>();
        for (AnalysisTaskLabel taskLabel : taskLabels) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("labelId", taskLabel.getLabelId());
            stat.put("labelName", taskLabel.getLabelName());
            stat.put("labelVersion", taskLabel.getLabelVersion());

            // 统计结果分布
            List<Object[]> distribution = labelResultRepository.countByResult(taskId, taskLabel.getLabelId());
            Map<String, Long> resultDistribution = new HashMap<>();
            long total = 0;
            for (Object[] row : distribution) {
                String resultValue = (String) row[0];
                Long count = (Long) row[1];
                resultDistribution.put(resultValue, count);
                total += count;
            }
            stat.put("resultDistribution", resultDistribution);
            stat.put("total", total);

            // 计算命中率
            Long hitCount = resultDistribution.getOrDefault(LabelResult.ResultValue.YES, 0L);
            stat.put("hitCount", hitCount);
            stat.put("hitRate", total > 0 ? (double) hitCount / total : 0.0);

            // 计算平均信心度
            BigDecimal avgConfidence = labelResultRepository.calculateAverageConfidence(taskId, taskLabel.getLabelId());
            stat.put("averageConfidence", avgConfidence);

            labelStats.add(stat);
        }

        statistics.put("labels", labelStats);
        statistics.put("totalRows", task.getTotalRows());
        statistics.put("processedRows", task.getProcessedRows());
        statistics.put("successRows", task.getSuccessRows());
        statistics.put("failedRows", task.getFailedRows());
        statistics.put("needsReviewCount", labelResultRepository.countByAnalysisTaskIdAndNeedsReviewTrue(taskId));
        statistics.put("modifiedCount", labelResultRepository.countByAnalysisTaskIdAndIsModifiedTrue(taskId));

        return statistics;
    }

    /**
     * 按数据行分页获取标签结果（解决分页问题）
     * 每页返回指定数量的数据行，每行包含所有标签的结果
     */
    public PageResult<Map<String, Object>> getResultsByDataRow(Integer taskId, Integer page, Integer size,
                                                                String resultFilter, Boolean onlyNeedsReview) {
        Integer userId = securityUtil.getCurrentUserId();

        // 验证任务归属
        AnalysisTask task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "分析任务不存在"));
        datasetRepository.findByIdAndUserId(task.getDatasetId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在"));

        // 分页获取不重复的数据行ID（native query已包含ORDER BY，不要在Pageable中重复添加）
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Object> dataRowIdPage = labelResultRepository.findDistinctDataRowIdsByAnalysisTaskId(taskId, pageable);

        if (dataRowIdPage.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0L, page, size);
        }

        // native query返回的类型是BigInteger，需要手动转换为Long
        List<Long> dataRowIds = dataRowIdPage.getContent().stream()
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toList());

        // 获取这些数据行的所有标签结果
        List<LabelResult> labelResults = labelResultRepository.findByAnalysisTaskIdAndDataRowIds(taskId, dataRowIds);

        // 获取数据行信息
        Map<Long, DataRow> dataRowMap = dataRowRepository.findAllById(dataRowIds).stream()
                .collect(Collectors.toMap(DataRow::getId, dr -> dr));

        // 获取标签名称映射
        List<Integer> labelIds = labelResults.stream()
                .map(LabelResult::getLabelId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, String> labelNameMap = labelRepository.findByIdIn(labelIds).stream()
                .collect(Collectors.toMap(Label::getId, Label::getName));

        // 按数据行分组
        Map<Long, List<LabelResult>> resultsByRow = labelResults.stream()
                .collect(Collectors.groupingBy(LabelResult::getDataRowId));

        // 构建结果列表，保持分页顺序
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long dataRowId : dataRowIds) {
            DataRow dataRow = dataRowMap.get(dataRowId);
            List<LabelResult> rowResults = resultsByRow.getOrDefault(dataRowId, Collections.emptyList());

            // 应用筛选条件
            if (onlyNeedsReview != null && onlyNeedsReview) {
                boolean hasNeedsReview = rowResults.stream().anyMatch(LabelResult::getNeedsReview);
                if (!hasNeedsReview) continue;
            }

            if (resultFilter != null && !resultFilter.isEmpty()) {
                boolean hasMatchingResult = rowResults.stream()
                        .anyMatch(r -> resultFilter.equals(r.getResult()));
                if (!hasMatchingResult) continue;
            }

            Map<String, Object> row = new HashMap<>();
            row.put("rowId", dataRowId);
            row.put("rowIndex", dataRow != null ? dataRow.getRowIndex() : null);
            row.put("originalData", dataRow != null ? dataRow.getOriginalData() : Collections.emptyMap());

            // 标签结果Map
            Map<String, Object> labelResultsMap = new HashMap<>();
            for (LabelResult lr : rowResults) {
                String labelName = labelNameMap.getOrDefault(lr.getLabelId(), "未知标签");
                Map<String, Object> resultInfo = new HashMap<>();
                resultInfo.put("result", lr.getResult());
                resultInfo.put("confidence", lr.getAiConfidence());
                resultInfo.put("needsReview", lr.getNeedsReview());
                resultInfo.put("isModified", lr.getIsModified());
                resultInfo.put("aiReason", lr.getAiReasoning());
                labelResultsMap.put(labelName, resultInfo);
            }
            row.put("labelResults", labelResultsMap);

            rows.add(row);
        }

        // 获取总数据行数
        long totalRows = labelResultRepository.countDistinctDataRowsByAnalysisTaskId(taskId);

        return PageResult.of(rows, totalRows, page, size);
    }
}