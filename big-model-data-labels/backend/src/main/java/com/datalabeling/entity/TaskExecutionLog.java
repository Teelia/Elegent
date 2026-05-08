package com.datalabeling.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务执行日志实体
 * 记录分析任务执行过程中的日志信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_execution_logs", indexes = {
    @Index(name = "idx_logs_task_id", columnList = "analysis_task_id"),
    @Index(name = "idx_logs_task_row", columnList = "analysis_task_id, row_index"),
    @Index(name = "idx_logs_created_at", columnList = "created_at"),
    @Index(name = "idx_logs_level", columnList = "analysis_task_id, log_level")
})
public class TaskExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 分析任务ID
     */
    @NotNull(message = "分析任务ID不能为空")
    @Column(name = "analysis_task_id", nullable = false)
    private Integer analysisTaskId;

    /**
     * 数据行ID（可为空，表示任务级日志）
     */
    @Column(name = "data_row_id")
    private Long dataRowId;

    /**
     * 行索引
     */
    @Column(name = "row_index")
    private Integer rowIndex;

    /**
     * 标签键
     */
    @Size(max = 150, message = "标签键长度不能超过150")
    @Column(name = "label_key", length = 150)
    private String labelKey;

    /**
     * 日志级别：INFO, WARN, ERROR
     */
    @NotBlank(message = "日志级别不能为空")
    @Size(max = 20, message = "日志级别长度不能超过20")
    @Column(name = "log_level", nullable = false, length = 20)
    private String logLevel = LogLevel.INFO;

    /**
     * 日志消息
     */
    @NotBlank(message = "日志消息不能为空")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * 信心度
     */
    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    /**
     * 处理耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 日志级别常量
     */
    public static class LogLevel {
        public static final String INFO = "INFO";
        public static final String WARN = "WARN";
        public static final String ERROR = "ERROR";
    }

    /**
     * 创建INFO级别日志
     */
    public static TaskExecutionLog info(Integer taskId, Long rowId, Integer rowIndex, String labelKey, String message) {
        return TaskExecutionLog.builder()
            .analysisTaskId(taskId)
            .dataRowId(rowId)
            .rowIndex(rowIndex)
            .labelKey(labelKey)
            .logLevel(LogLevel.INFO)
            .message(message)
            .build();
    }

    /**
     * 创建INFO级别日志（带信心度和耗时）
     */
    public static TaskExecutionLog info(Integer taskId, Long rowId, Integer rowIndex, String labelKey, 
                                        String message, BigDecimal confidence, Integer durationMs) {
        return TaskExecutionLog.builder()
            .analysisTaskId(taskId)
            .dataRowId(rowId)
            .rowIndex(rowIndex)
            .labelKey(labelKey)
            .logLevel(LogLevel.INFO)
            .message(message)
            .confidence(confidence)
            .durationMs(durationMs)
            .build();
    }

    /**
     * 创建WARN级别日志
     */
    public static TaskExecutionLog warn(Integer taskId, Long rowId, Integer rowIndex, String labelKey, String message) {
        return TaskExecutionLog.builder()
            .analysisTaskId(taskId)
            .dataRowId(rowId)
            .rowIndex(rowIndex)
            .labelKey(labelKey)
            .logLevel(LogLevel.WARN)
            .message(message)
            .build();
    }

    /**
     * 创建ERROR级别日志
     */
    public static TaskExecutionLog error(Integer taskId, Long rowId, Integer rowIndex, String labelKey, String message) {
        return TaskExecutionLog.builder()
            .analysisTaskId(taskId)
            .dataRowId(rowId)
            .rowIndex(rowIndex)
            .labelKey(labelKey)
            .logLevel(LogLevel.ERROR)
            .message(message)
            .build();
    }

    /**
     * 创建任务级日志
     */
    public static TaskExecutionLog taskLog(Integer taskId, String level, String message) {
        return TaskExecutionLog.builder()
            .analysisTaskId(taskId)
            .logLevel(level)
            .message(message)
            .build();
    }
}