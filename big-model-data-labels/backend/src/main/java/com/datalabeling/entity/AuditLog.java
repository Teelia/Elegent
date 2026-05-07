package com.datalabeling.entity;

import com.datalabeling.converter.JsonConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 操作日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
    @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
})
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 操作：login, create_label, upload_file, export_data等
     */
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 50, message = "操作类型长度不能超过50")
    @Column(nullable = false, length = 50)
    private String action;

    /**
     * 资源类型：label, task, data_row
     */
    @Size(max = 50, message = "资源类型长度不能超过50")
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * 资源ID
     */
    @Column(name = "resource_id")
    private Integer resourceId;

    /**
     * 详细信息（JSON格式）
     */
    @Convert(converter = JsonConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> details;

    /**
     * IP地址
     */
    @Size(max = 45, message = "IP地址长度不能超过45")
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User Agent
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * 创建时间
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
