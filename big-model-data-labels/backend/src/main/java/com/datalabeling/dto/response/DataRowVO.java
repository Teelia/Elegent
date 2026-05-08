package com.datalabeling.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据行视图对象
 */
@Data
public class DataRowVO {

    /**
     * 数据行ID
     */
    private Long id;

    /**
     * 任务ID
     */
    private Integer taskId;

    /**
     * 行索引
     */
    private Integer rowIndex;

    /**
     * 原始数据
     */
    private Map<String, Object> originalData;

    /**
     * 标签结果
     * 格式：{"标签名_v1": "是", "标签名_v2": "否"}
     */
    private Map<String, Object> labelResults;

    /**
     * AI信心度
     * 格式：{"标签名_v1": 0.95, "标签名_v2": 0.88}
     */
    private Map<String, Object> aiConfidence;

    /**
     * AI分析原因
     * 格式：{"标签名_v1": "包含正面词汇", "标签名_v2": "..."}
     */
    private Map<String, Object> aiReasoning;

    /**
     * 信心度采纳阈值
     */
    private BigDecimal confidenceThreshold;

    /**
     * 是否需要人工审核
     */
    private Boolean needsReview;

    /**
     * 是否被手动修改
     */
    private Boolean isModified;

    /**
     * 处理状态
     */
    private String processingStatus;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
