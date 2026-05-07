package com.datalabeling.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 标签结果实体
 * 存储某个标签对某行数据的分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "label_results",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_row_task_label", columnNames = {"data_row_id", "analysis_task_id", "label_id"})
    },
    indexes = {
        @Index(name = "idx_label_results_row_id", columnList = "data_row_id"),
        @Index(name = "idx_label_results_task_id", columnList = "analysis_task_id"),
        @Index(name = "idx_label_results_label_id", columnList = "label_id"),
        @Index(name = "idx_label_results_needs_review", columnList = "analysis_task_id, needs_review"),
        @Index(name = "idx_label_results_result", columnList = "analysis_task_id, label_id, result")
    })
public class LabelResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 数据行ID
     */
    @NotNull(message = "数据行ID不能为空")
    @Column(name = "data_row_id", nullable = false)
    private Long dataRowId;

    /**
     * 分析任务ID
     */
    @NotNull(message = "分析任务ID不能为空")
    @Column(name = "analysis_task_id", nullable = false)
    private Integer analysisTaskId;

    /**
     * 标签ID
     */
    @NotNull(message = "标签ID不能为空")
    @Column(name = "label_id", nullable = false)
    private Integer labelId;

    /**
     * 标签键（name_v版本）
     */
    @NotBlank(message = "标签键不能为空")
    @Size(max = 150, message = "标签键长度不能超过150")
    @Column(name = "label_key", nullable = false, length = 150)
    private String labelKey;

    /**
     * 结果：分类标签为是/否，提取标签为摘要
     */
    @Size(max = 500, message = "结果长度不能超过500")
    @Column(nullable = false, length = 500)
    private String result;

    /**
     * AI信心度（0.00-1.00）
     */
    @Column(name = "ai_confidence", precision = 3, scale = 2)
    private BigDecimal aiConfidence;

    /**
     * AI分析原因
     */
    @Size(max = 500, message = "AI分析原因长度不能超过500")
    @Column(name = "ai_reasoning", length = 500)
    private String aiReasoning;

    /**
     * 提取的数据（JSON格式，仅提取类型标签使用）
     * 格式：{"姓名":"张三","手机号":"138xxx"}
     */
    @Column(name = "extracted_data", columnDefinition = "JSON")
    private String extractedData;

    /**
     * 标签类型（冗余字段，便于查询）
     */
    @Size(max = 30)
    @Column(name = "label_type", length = 30)
    private String labelType;

    /**
     * 信心度采纳阈值
     */
    @Column(name = "confidence_threshold", precision = 3, scale = 2)
    private BigDecimal confidenceThreshold = new BigDecimal("0.80");

    /**
     * 是否需要人工审核
     */
    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = false;

    /**
     * 是否被人工修改
     */
    @Column(name = "is_modified", nullable = false)
    private Boolean isModified = false;

    /**
     * 处理状态：pending, success, failed
     */
    @Size(max = 20, message = "处理状态长度不能超过20")
    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = ProcessingStatus.PENDING;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 处理状态常量
     */
    public static class ProcessingStatus {
        public static final String PENDING = "pending";
        public static final String SUCCESS = "success";
        public static final String FAILED = "failed";
    }

    /**
     * 结果值常量
     */
    public static class ResultValue {
        public static final String YES = "是";
        public static final String NO = "否";
    }

    /**
     * 判断是否需要审核（信心度低于阈值）
     */
    public void updateNeedsReview() {
        if (aiConfidence != null && confidenceThreshold != null) {
            this.needsReview = aiConfidence.compareTo(confidenceThreshold) < 0;
        }
    }

    /**
     * 获取信心度百分比
     */
    public Integer getConfidencePercentage() {
        if (aiConfidence == null) {
            return null;
        }
        return aiConfidence.multiply(new BigDecimal("100")).intValue();
    }
}