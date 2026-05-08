package com.datalabeling.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 分析任务-标签关联实体
 * 记录分析任务使用的标签及其快照信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analysis_task_labels",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_label", columnNames = {"analysis_task_id", "label_id"})
    },
    indexes = {
        @Index(name = "idx_task_labels_task_id", columnList = "analysis_task_id"),
        @Index(name = "idx_task_labels_label_id", columnList = "label_id")
    })
public class AnalysisTaskLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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
     * 标签名称（快照）
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
    @Column(name = "label_name", nullable = false, length = 100)
    private String labelName;

    /**
     * 标签版本（快照）
     */
    @NotNull(message = "标签版本不能为空")
    @Column(name = "label_version", nullable = false)
    private Integer labelVersion;

    /**
     * 标签描述（快照）
     */
    @Column(name = "label_description", columnDefinition = "TEXT")
    private String labelDescription;

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
     * 构建标签键（name_v版本）
     */
    public String getLabelKey() {
        return labelName + "_v" + labelVersion;
    }
}