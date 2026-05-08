package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.AnalysisTaskVO;
import com.datalabeling.entity.AnalysisTask;
import com.datalabeling.entity.AnalysisTaskLabel;
import com.datalabeling.entity.Label;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分析任务Mapper
 */
@Component
public class AnalysisTaskMapper {
    
    /**
     * Entity转VO
     */
    public AnalysisTaskVO toVO(AnalysisTask entity) {
        if (entity == null) {
            return null;
        }
        
        return AnalysisTaskVO.builder()
                .id(entity.getId())
                .datasetId(entity.getDatasetId())
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .statusDisplay(AnalysisTaskVO.getStatusDisplayName(entity.getStatus()))
                .totalRows(entity.getTotalRows())
                .processedRows(entity.getProcessedRows())
                .successRows(entity.getSuccessRows())
                .failedRows(entity.getFailedRows())
                .progressPercent(AnalysisTaskVO.calculateProgressPercent(entity.getTotalRows(), entity.getProcessedRows()))
                .defaultConfidenceThreshold(entity.getDefaultConfidenceThreshold())
                .modelConfigId(entity.getModelConfigId())
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .estimatedMinutesRemaining(AnalysisTaskVO.estimateRemainingMinutes(
                        entity.getTotalRows(), entity.getProcessedRows(), entity.getStartedAt()))
                .build();
    }
    
    /**
     * Entity转VO（带数据集名称）
     */
    public AnalysisTaskVO toVO(AnalysisTask entity, String datasetName) {
        AnalysisTaskVO vo = toVO(entity);
        if (vo != null) {
            vo.setDatasetName(datasetName);
        }
        return vo;
    }
    
    /**
     * Entity转VO（带完整信息）
     */
    public AnalysisTaskVO toVO(AnalysisTask entity, String datasetName,
                               List<AnalysisTaskLabel> taskLabels,
                               Map<Integer, Label> labelMap,
                               Map<Integer, Long> hitCountMap,
                               Long needsReviewCount, Long modifiedCount) {
        AnalysisTaskVO vo = toVO(entity, datasetName);
        if (vo == null) {
            return null;
        }
        
        // 设置标签信息
        if (taskLabels != null && !taskLabels.isEmpty()) {
            List<AnalysisTaskVO.TaskLabelInfo> labelInfos = new ArrayList<>();
            for (AnalysisTaskLabel taskLabel : taskLabels) {
                Label label = labelMap != null ? labelMap.get(taskLabel.getLabelId()) : null;
                Long hitCount = hitCountMap != null ? hitCountMap.get(taskLabel.getLabelId()) : null;
                
                // 获取focusColumns的第一个作为focusColumn（兼容旧设计）
                String focusColumn = null;
                if (label != null && label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
                    focusColumn = label.getFocusColumns().get(0);
                }
                
                AnalysisTaskVO.TaskLabelInfo info = AnalysisTaskVO.TaskLabelInfo.builder()
                        .labelId(taskLabel.getLabelId())
                        .labelName(taskLabel.getLabelName())
                        .labelDescription(taskLabel.getLabelDescription())
                        .focusColumn(focusColumn)
                        .labelSnapshot(buildLabelSnapshot(taskLabel))
                        .hitCount(hitCount)
                        .build();
                
                // 计算命中率
                if (hitCount != null && entity.getTotalRows() != null && entity.getTotalRows() > 0) {
                    info.setHitRate((double) hitCount / entity.getTotalRows());
                }
                
                labelInfos.add(info);
            }
            vo.setLabels(labelInfos);
        }
        
        vo.setNeedsReviewCount(needsReviewCount);
        vo.setModifiedCount(modifiedCount);
        
        return vo;
    }
    
    /**
     * 构建标签快照字符串
     */
    private String buildLabelSnapshot(AnalysisTaskLabel taskLabel) {
        return String.format("%s_v%d", taskLabel.getLabelName(), taskLabel.getLabelVersion());
    }
}