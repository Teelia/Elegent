package com.datalabeling.entity;

import com.datalabeling.converter.JsonConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 数据行实体
 * 存储数据集中的每一行原始数据
 *
 * 注意：在新架构中，标签结果存储在 label_results 表中
 * 但为了向后兼容，保留了 labelResults 等字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "data_rows",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_row", columnNames = {"task_id", "row_index"})
    },
    indexes = {
        @Index(name = "idx_data_rows_task_id", columnList = "task_id"),
        @Index(name = "idx_data_rows_task_row", columnList = "task_id, row_index"),
        @Index(name = "idx_data_rows_status", columnList = "processing_status"),
        @Index(name = "idx_data_rows_needs_review", columnList = "task_id, needs_review")
    })
public class DataRow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务ID / 数据集ID
     * 在新架构中，这个字段实际上是 datasetId
     * 为了向后兼容，保留 task_id 列名
     */
    @NotNull(message = "任务ID不能为空")
    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    /**
     * 行索引（从0开始）
     */
    @NotNull(message = "行索引不能为空")
    @Min(value = 0, message = "行索引不能为负数")
    @Column(name = "row_index", nullable = false)
    private Integer rowIndex;

    /**
     * 原始数据（JSON格式）
     */
    @NotNull(message = "原始数据不能为空")
    @Convert(converter = JsonConverter.class)
    @Column(name = "original_data", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> originalData;

    /**
     * 标签结果（JSON格式）- 向后兼容
     * 格式：{"标签名_v1": "是", "标签名_v2": "否"}
     *
     * 注意：在新架构中，标签结果存储在 label_results 表中
     * 此字段保留用于向后兼容旧代码
     */
    @Convert(converter = JsonConverter.class)
    @Column(name = "label_results", columnDefinition = "JSON")
    private Map<String, Object> labelResults;

    /**
     * AI信心度（JSON格式）
     * 格式：{"标签名_v1": 0.95, "标签名_v2": 0.88}
     */
    @Convert(converter = JsonConverter.class)
    @Column(name = "ai_confidence", columnDefinition = "JSON")
    private Map<String, Object> aiConfidence;

    /**
     * AI分析原因（JSON格式）
     * 格式：{"标签名_v1": "包含正面词汇", "标签名_v2": "..."}
     */
    @Convert(converter = JsonConverter.class)
    @Column(name = "ai_reasoning", columnDefinition = "JSON")
    private Map<String, Object> aiReasoning;

    /**
     * 信心度采纳阈值（默认0.80）
     */
    @Column(name = "confidence_threshold", precision = 3, scale = 2)
    private BigDecimal confidenceThreshold = new BigDecimal("0.80");

    /**
     * 是否需要人工审核
     * 当AI信心度低于阈值时，自动标记为需要审核
     */
    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = false;

    /**
     * 是否被手动修改 - 向后兼容
     *
     * 注意：在新架构中，此信息存储在 label_results 表的 is_modified 字段
     */
    @Column(name = "is_modified", nullable = false)
    private Boolean isModified = false;

    /**
     * 处理状态：pending, processing, success, failed - 向后兼容
     *
     * 注意：在新架构中，此信息存储在 label_results 表的 processing_status 字段
     */
    @Size(max = 20, message = "处理状态长度不能超过20")
    @Column(name = "processing_status", nullable = false, length = 20)
    private String processingStatus = "pending";

    /**
     * 错误信息 - 向后兼容
     *
     * 注意：在新架构中，此信息存储在 label_results 表的 error_message 字段
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 获取数据集ID（新架构中使用）
     * 实际上就是 taskId，只是语义上更准确
     */
    @Transient
    public Integer getDatasetId() {
        return taskId;
    }

    /**
     * 设置数据集ID（新架构中使用）
     */
    @Transient
    public void setDatasetId(Integer datasetId) {
        this.taskId = datasetId;
    }

    /**
     * 更新是否需要审核状态
     * 检查所有标签的信心度，如果有任何一个低于阈值，则标记为需要审核
     */
    public void updateNeedsReview() {
        if (aiConfidence == null || aiConfidence.isEmpty()) {
            this.needsReview = false;
            return;
        }
        
        BigDecimal threshold = this.confidenceThreshold != null ?
            this.confidenceThreshold : new BigDecimal("0.80");
        
        for (Object value : aiConfidence.values()) {
            BigDecimal confidence;
            if (value instanceof Number) {
                confidence = new BigDecimal(value.toString());
            } else {
                continue;
            }
            
            if (confidence.compareTo(threshold) < 0) {
                this.needsReview = true;
                return;
            }
        }
        this.needsReview = false;
    }
}
