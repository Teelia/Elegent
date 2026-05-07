package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分析任务视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskVO {
    
    /**
     * 任务ID
     */
    private Integer id;
    
    /**
     * 数据集ID
     */
    private Integer datasetId;
    
    /**
     * 数据集名称
     */
    private String datasetName;
    
    /**
     * 任务名称
     */
    private String name;
    
    /**
     * 任务描述
     */
    private String description;
    
    /**
     * 状态：pending, processing, paused, completed, failed
     */
    private String status;
    
    /**
     * 状态显示名称
     */
    private String statusDisplay;
    
    /**
     * 总行数
     */
    private Integer totalRows;
    
    /**
     * 已处理行数
     */
    private Integer processedRows;
    
    /**
     * 成功行数
     */
    private Integer successRows;
    
    /**
     * 失败行数
     */
    private Integer failedRows;
    
    /**
     * 进度百分比
     */
    private Integer progressPercent;
    
    /**
     * 默认信心度阈值
     */
    private BigDecimal defaultConfidenceThreshold;
    
    /**
     * 开始时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 关联的标签列表
     */
    private List<TaskLabelInfo> labels;
    
    /**
     * 需要审核的数量
     */
    private Long needsReviewCount;
    
    /**
     * 已修改的数量
     */
    private Long modifiedCount;
    
    /**
     * 预计剩余时间（分钟）
     */
    private Integer estimatedMinutesRemaining;

    /**
     * 使用的模型配置ID
     */
    private Integer modelConfigId;

    /**
     * 使用的模型配置名称
     */
    private String modelConfigName;

    /**
     * 任务标签信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskLabelInfo {
        /**
         * 标签ID
         */
        private Integer labelId;
        
        /**
         * 标签名称
         */
        private String labelName;
        
        /**
         * 标签描述
         */
        private String labelDescription;
        
        /**
         * 关注列
         */
        private String focusColumn;
        
        /**
         * 标签快照（创建任务时的配置）
         */
        private String labelSnapshot;
        
        /**
         * 命中数量（结果为"是"的数量）
         */
        private Long hitCount;
        
        /**
         * 命中率
         */
        private Double hitRate;
    }
    
    /**
     * 获取状态显示名称
     */
    public static String getStatusDisplayName(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "pending":
                return "待启动";
            case "processing":
                return "进行中";
            case "paused":
                return "已暂停";
            case "completed":
                return "已完成";
            case "failed":
                return "失败";
            default:
                return status;
        }
    }
    
    /**
     * 计算进度百分比
     */
    public static Integer calculateProgressPercent(Integer totalRows, Integer processedRows) {
        if (totalRows == null || totalRows == 0) {
            return 0;
        }
        if (processedRows == null) {
            return 0;
        }
        return (int) Math.round((double) processedRows / totalRows * 100);
    }
    
    /**
     * 估算剩余时间（基于已处理速度）
     */
    public static Integer estimateRemainingMinutes(Integer totalRows, Integer processedRows, 
                                                    LocalDateTime startedAt) {
        if (totalRows == null || processedRows == null || processedRows == 0 || startedAt == null) {
            return null;
        }
        int remaining = totalRows - processedRows;
        if (remaining <= 0) {
            return 0;
        }
        
        long elapsedSeconds = java.time.Duration.between(startedAt, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds <= 0) {
            return null;
        }
        
        double rowsPerSecond = (double) processedRows / elapsedSeconds;
        if (rowsPerSecond <= 0) {
            return null;
        }
        
        return (int) Math.ceil(remaining / rowsPerSecond / 60);
    }
}