package com.datalabeling.entity;

import com.datalabeling.converter.JsonConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/**
 * 数据库同步配置实体（支持导入和导出双方向）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sync_configs", indexes = {
    @Index(name = "idx_sync_configs_user_id", columnList = "user_id"),
    @Index(name = "idx_direction", columnList = "direction"),
    @Index(name = "idx_connection_test", columnList = "connection_test_status")
})
public class SyncConfig extends BaseEntity {

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
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 数据库类型：postgresql, mysql, sqlserver, oracle
     */
    @NotBlank(message = "数据库类型不能为空")
    @Size(max = 20, message = "数据库类型长度不能超过20")
    @Column(name = "db_type", nullable = false, length = 20)
    private String dbType;

    /**
     * 数据方向：import=导入, export=导出
     */
    @Column(name = "direction", length = 10)
    private String direction = "export";

    /**
     * 主机地址
     */
    @NotBlank(message = "主机地址不能为空")
    @Size(max = 255, message = "主机地址长度不能超过255")
    @Column(nullable = false)
    private String host;

    /**
     * 端口
     */
    @NotNull(message = "端口不能为空")
    @Min(value = 1, message = "端口必须大于0")
    @Max(value = 65535, message = "端口不能超过65535")
    @Column(nullable = false)
    private Integer port;

    /**
     * 数据库名（非 Oracle 数据库使用）
     */
    @Size(max = 100, message = "数据库名长度不能超过100")
    @Column(name = "database_name", length = 100)
    private String databaseName;

    // ========== Oracle 专用字段 ==========

    /**
     * Oracle SID（当 connection_mode=sid 时使用）
     */
    @Size(max = 100, message = "SID长度不能超过100")
    @Column(name = "oracle_sid", length = 100)
    private String oracleSid;

    /**
     * Oracle Service Name（当 connection_mode=service_name 时使用）
     */
    @Size(max = 100, message = "Service Name长度不能超过100")
    @Column(name = "oracle_service_name", length = 100)
    private String oracleServiceName;

    /**
     * 连接模式：standard=标准, sid=SID模式, service_name=ServiceName模式, tns=TNS模式
     */
    @Column(name = "connection_mode", length = 20)
    private String connectionMode = "standard";

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名长度不能超过100")
    @Column(nullable = false, length = 100)
    private String username;

    /**
     * 加密后的密码
     */
    @NotBlank(message = "密码不能为空")
    @Column(name = "password_encrypted", nullable = false, columnDefinition = "TEXT")
    private String passwordEncrypted;

    /**
     * 表名（导出时为目标表，导入时为源表）
     */
    @Size(max = 100, message = "表名长度不能超过100")
    @Column(name = "table_name", length = 100)
    private String tableName;

    /**
     * 字段映射（JSON格式，导出时需要）
     * 格式：{"文件列名": "数据库字段名"}
     */
    @Convert(converter = JsonConverter.class)
    @Column(name = "field_mappings", columnDefinition = "JSON")
    private Map<String, Object> fieldMappings;

    // ========== 导入专用字段 ==========

    /**
     * 自定义 SQL 查询条件（仅导入时使用）
     * 示例：created_at > '2024-01-01' AND status = 'active'
     */
    @Lob
    @Column(name = "import_query", columnDefinition = "TEXT")
    private String importQuery;

    /**
     * 上次导入时间（用于增量更新）
     */
    @Column(name = "last_import_time")
    private java.time.LocalDateTime lastImportTime;

    /**
     * 导入状态：pending, importing, completed, failed
     */
    @Column(name = "import_status", length = 20)
    private String importStatus;

    /**
     * 连接测试状态：success, failed, unknown
     */
    @Column(name = "connection_test_status", length = 20)
    private String connectionTestStatus;

    /**
     * 最后测试连接时间
     */
    @Column(name = "connection_test_time")
    private java.time.LocalDateTime connectionTestTime;

    /**
     * 时间戳列名（用于增量更新）
     * 当该字段有值时，增量更新将基于此列进行过滤
     */
    @Size(max = 100, message = "时间戳列名长度不能超过100")
    @Column(name = "timestamp_column", length = 100)
    private String timestampColumn;

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
