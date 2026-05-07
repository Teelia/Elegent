package com.datalabeling.entity;

import com.datalabeling.converter.JsonConverter;
import com.datalabeling.converter.StringListConverter;
import com.datalabeling.service.constant.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件任务实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "file_tasks", indexes = {
    @Index(name = "idx_file_tasks_user_id", columnList = "user_id"),
    @Index(name = "idx_file_tasks_status", columnList = "status"),
    @Index(name = "idx_file_tasks_created_at", columnList = "created_at")
})
public class FileTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 文件名
     */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255")
    @Column(nullable = false)
    private String filename;

    /**
     * 原始文件名
     */
    @NotBlank(message = "原始文件名不能为空")
    @Size(max = 255, message = "原始文件名长度不能超过255")
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    /**
     * 文件路径
     */
    @NotBlank(message = "文件路径不能为空")
    @Size(max = 500, message = "文件路径长度不能超过500")
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 文件哈希（SHA256，用于去重）
     */
    @Size(max = 64, message = "文件哈希长度不能超过64")
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /**
     * 文件列信息
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "columns", columnDefinition = "JSON")
    private List<String> columns;

    /**
     * 状态：uploaded, pending, processing, paused, completed, failed, cancelled, archived
     */
    @NotBlank(message = "状态不能为空")
    @Column(nullable = false, length = 20)
    private String status;

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
     * 失败行数
     */
    @Min(value = 0, message = "失败行数不能为负数")
    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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
     * 归档时间
     */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /**
     * 暂停时间
     */
    @Column(name = "paused_at")
    private LocalDateTime pausedAt;

    /**
     * 运行时使用的模型配置名称（快照）
     */
    @Column(name = "run_model_config_name", length = 100)
    private String runModelConfigName;

    /**
     * 运行时是否包含推理过程
     */
    @Column(name = "run_include_reasoning")
    private Boolean runIncludeReasoning;

    // ==================== 业务方法 ====================

    /**
     * 判断是否可以配置标签
     */
    @Transient
    public boolean canConfigureLabels() {
        return TaskStatus.canConfigureLabels(this.status);
    }

    /**
     * 判断是否可以启动分析
     */
    @Transient
    public boolean canStart() {
        return TaskStatus.canStart(this.status);
    }

    /**
     * 判断是否正在处理中
     */
    @Transient
    public boolean isProcessing() {
        return TaskStatus.isProcessing(this.status);
    }

    /**
     * 判断是否已完成
     */
    @Transient
    public boolean isFinished() {
        return TaskStatus.isFinished(this.status);
    }

    /**
     * 计算进度百分比
     */
    @Transient
    public int getPercentage() {
        if (totalRows == null || totalRows == 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(processedRows * 100.0 / totalRows));
    }
}
