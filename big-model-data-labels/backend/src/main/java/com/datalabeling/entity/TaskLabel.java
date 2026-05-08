package com.datalabeling.entity;

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

/**
 * 任务标签关联实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "task_labels",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_label", columnNames = {"task_id", "label_id"})
    },
    indexes = {
        @Index(name = "idx_task_labels_task_id", columnList = "task_id"),
        @Index(name = "idx_task_labels_label_id", columnList = "label_id")
    })
public class TaskLabel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    @Column(name = "task_id", nullable = false)
    private Integer taskId;

    /**
     * 标签ID
     */
    @NotNull(message = "标签ID不能为空")
    @Column(name = "label_id", nullable = false)
    private Integer labelId;

    /**
     * 标签名称（冗余字段，用于历史记录）
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
    @Column(name = "label_name", nullable = false, length = 100)
    private String labelName;

    /**
     * 标签版本（冗余字段，用于历史记录）
     */
    @NotNull(message = "标签版本不能为空")
    @Min(value = 1, message = "标签版本必须大于0")
    @Column(name = "label_version", nullable = false)
    private Integer labelVersion;

    /**
     * 标签描述（冗余字段，用于历史记录）
     */
    @Column(name = "label_description", columnDefinition = "TEXT")
    private String labelDescription;
}
