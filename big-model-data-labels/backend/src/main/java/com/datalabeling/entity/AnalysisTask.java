package com.datalabeling.entity;

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
import java.time.LocalDateTime;

/**
 * 分析任务实体
 * 代表对数据集执行的一次分析操作
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "analysis_tasks", indexes = {
    @Index(name = "idx_analysis_tasks_dataset_id", columnList = "dataset_id"),
    @Index(name = "idx_analysis_tasks_user_id", columnList = "user_id"),
    @Index(name = "idx_analysis_tasks_status", columnList = "status"),
    @Index(name = "idx_analysis_tasks_created_at", columnList = "created_at")
})
public class AnalysisTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 数据集ID
     */
    @NotNull(message = "数据集ID不能为空")
    @Column(name = "dataset_id", nullable = false)
    private Integer datasetId;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 任务名称（可选）
     */
    @Size(max = 200, message = "任务名称长度不能超过200")
    @Column(length = 200)
    private String name;

    /**
     * 任务描述
     */
    @Size(max = 500, message = "任务描述长度不能超过500")
    @Column(length = 500)
    private String description;

    /**
     * 状态：pending, processing, paused, completed, failed, cancelled
     */
    @Column(nullable = false, length = 20)
    private String status = Status.PENDING;

    /**
     * 总行数
     */
    @Min(value = 0, message = "总行数不能为负数")
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    /**
     * 已处理行数
     */
    @Min(value = 0, message = "已处理行数不能为负数")
    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows = 0;

    /**
     * 成功行数
     */
    @Min(value = 0, message = "成功行数不能为负数")
    @Column(name = "success_rows", nullable = false)
    private Integer successRows = 0;

    /**
     * 失败行数
     */
    @Min(value = 0, message = "失败行数不能为负数")
    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    /**
     * 默认信心度阈值
     */
    @Column(name = "default_confidence_threshold", precision = 3, scale = 2)
    private BigDecimal defaultConfidenceThreshold = new BigDecimal("0.80");

    /**
     * 使用的大模型配置ID
     */
    @Column(name = "model_config_id")
    private Integer modelConfigId;

    /**
     * 并发处理数量（同时处理多少条数据）
     */
    @Min(value = 1, message = "并发数不能小于1")
    @Column(name = "concurrency", nullable = false)
    @Builder.Default
    private Integer concurrency = 1;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 最后活动时间（用于检测任务是否卡住）
     */
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * 开始时间
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 任务状态常量
     */
    public static class Status {
        public static final String PENDING = "pending";
        public static final String PROCESSING = "processing";
        public static final String PAUSED = "paused";
        public static final String COMPLETED = "completed";
        public static final String FAILED = "failed";
        public static final String CANCELLED = "cancelled";
    }

    /**
     * 计算进度百分比
     */
    public int getPercentage() {
        if (totalRows == null || totalRows == 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(processedRows * 100.0 / totalRows));
    }

    /**
     * 判断任务是否已完成（包括成功、失败、取消）
     */
    public boolean isFinished() {
        return Status.COMPLETED.equals(status) 
            || Status.FAILED.equals(status) 
            || Status.CANCELLED.equals(status);
    }

    /**
     * 判断任务是否正在处理
     */
    public boolean isProcessing() {
        return Status.PROCESSING.equals(status);
    }
}