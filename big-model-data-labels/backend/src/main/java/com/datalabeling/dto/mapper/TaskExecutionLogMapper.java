package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.TaskExecutionLogVO;
import com.datalabeling.entity.TaskExecutionLog;
import org.springframework.stereotype.Component;

/**
 * 任务执行日志Mapper
 */
@Component
public class TaskExecutionLogMapper {
    
    /**
     * Entity转VO
     */
    public TaskExecutionLogVO toVO(TaskExecutionLog entity) {
        if (entity == null) {
            return null;
        }
        
        // 构建详情信息（包含信心度和耗时）
        String details = buildDetails(entity);
        
        return TaskExecutionLogVO.builder()
                .id(entity.getId())
                .taskId(entity.getAnalysisTaskId())
                .rowId(entity.getDataRowId())
                .rowNumber(entity.getRowIndex())
                .logLevel(entity.getLogLevel())
                .logLevelIcon(TaskExecutionLogVO.getLogLevelIcon(entity.getLogLevel()))
                .message(entity.getMessage())
                .details(details)
                .createdAt(entity.getCreatedAt())
                .timeDisplay(TaskExecutionLogVO.formatTimeDisplay(entity.getCreatedAt()))
                .build();
    }
    
    /**
     * Entity转VO（带行号）
     */
    public TaskExecutionLogVO toVO(TaskExecutionLog entity, Integer rowNumber) {
        TaskExecutionLogVO vo = toVO(entity);
        if (vo != null && rowNumber != null) {
            vo.setRowNumber(rowNumber);
        }
        return vo;
    }
    
    /**
     * 构建详情信息
     */
    private String buildDetails(TaskExecutionLog entity) {
        StringBuilder sb = new StringBuilder();
        
        if (entity.getLabelKey() != null) {
            sb.append("标签: ").append(entity.getLabelKey());
        }
        
        if (entity.getConfidence() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("信心度: ").append(entity.getConfidence().multiply(new java.math.BigDecimal("100")).intValue()).append("%");
        }
        
        if (entity.getDurationMs() != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("耗时: ").append(entity.getDurationMs()).append("ms");
        }
        
        return sb.length() > 0 ? sb.toString() : null;
    }
}