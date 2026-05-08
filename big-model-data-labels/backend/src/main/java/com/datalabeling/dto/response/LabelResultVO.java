package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 标签结果视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelResultVO {
    
    /**
     * 结果ID
     */
    private Long id;
    
    /**
     * 任务ID
     */
    private Integer taskId;
    
    /**
     * 数据行ID
     */
    private Long rowId;
    
    /**
     * 数据行号（显示用）
     */
    private Integer rowIndex;
    
    /**
     * 标签ID
     */
    private Integer labelId;
    
    /**
     * 标签名称
     */
    private String labelName;

    /**
     * 标签类型：classification(分类判断), extraction(信息提取)
     */
    private String labelType;

    /**
     * 结果值（分类标签为是/否，提取标签为摘要）
     */
    private String result;

    /**
     * 提取的数据（仅提取类型标签使用）
     * 格式：{"姓名":"张三","手机号":"138xxx"}
     */
    private Map<String, Object> extractedData;

    /**
     * AI信心度（0-1）
     */
    private BigDecimal aiConfidence;
    
    /**
     * AI信心度百分比显示
     */
    private Integer aiConfidencePercent;
    
    /**
     * 信心度阈值
     */
    private BigDecimal confidenceThreshold;
    
    /**
     * 信心度阈值百分比显示
     */
    private Integer confidenceThresholdPercent;
    
    /**
     * 是否需要人工审核
     */
    private Boolean needsReview;
    
    /**
     * 是否已人工修改
     */
    private Boolean isModified;
    
    /**
     * AI分析原因
     */
    private String aiReason;
    
    /**
     * AI提取的关键词
     */
    private String aiKeywords;
    
    /**
     * 原始数据（关注列的内容）
     */
    private String originalContent;
    
    /**
     * 完整行数据
     */
    private Map<String, Object> originalData;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 将BigDecimal信心度转换为百分比整数
     */
    public static Integer toPercent(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(BigDecimal.valueOf(100)).intValue();
    }
    
    /**
     * 获取信心度等级
     */
    public String getConfidenceLevel() {
        if (aiConfidence == null) {
            return "unknown";
        }
        double confidence = aiConfidence.doubleValue();
        if (confidence >= 0.9) {
            return "high";
        } else if (confidence >= 0.7) {
            return "medium";
        } else {
            return "low";
        }
    }
}