package com.datalabeling.entity;

import com.datalabeling.converter.JsonListConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 数据集实体
 * 代表用户上传的一个数据集（数据已解析存入数据库，不保存原始文件）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "datasets", indexes = {
    @Index(name = "idx_datasets_user_id", columnList = "user_id"),
    @Index(name = "idx_datasets_status", columnList = "status"),
    @Index(name = "idx_datasets_created_at", columnList = "created_at"),
    @Index(name = "idx_source_type", columnList = "source_type"),
    @Index(name = "idx_external_source_id", columnList = "external_source_id")
})
public class Dataset extends BaseEntity {

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
     * 数据集名称（用户可自定义，默认为原始文件名）
     */
    @NotBlank(message = "数据集名称不能为空")
    @Size(max = 200, message = "数据集名称长度不能超过200")
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 原始文件名（上传时的文件名，仅作记录）
     */
    @Size(max = 255, message = "原始文件名长度不能超过255")
    @Column(name = "original_filename")
    private String originalFilename;

    /**
     * 列信息（JSON格式）
     * 格式：[{"index":0,"name":"列名","dataType":"文本","nonNullRate":100}]
     */
    @Convert(converter = JsonListConverter.class)
    @Column(name = "columns", columnDefinition = "JSON")
    private List<Map<String, Object>> columns;

    /**
     * 总行数
     */
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    /**
     * 状态：uploaded（已上传）, archived（已归档）
     */
    @NotBlank(message = "状态不能为空")
    @Column(nullable = false, length = 20)
    private String status = "uploaded";

    /**
     * 描述
     */
    @Size(max = 500, message = "描述长度不能超过500")
    @Column(length = 500)
    private String description;

    /**
     * 数据来源类型：file=文件上传, database=数据库导入
     */
    @Column(name = "source_type", length = 20)
    private String sourceType = "file";

    /**
     * 外部数据源ID（当 source_type=database 时）
     */
    @Column(name = "external_source_id")
    private Integer externalSourceId;

    /**
     * 导入时使用的 SQL 查询条件
     */
    @Lob
    @Column(name = "import_query", columnDefinition = "TEXT")
    private String importQuery;

    /**
     * 最后导入时间（用于增量更新）
     */
    @Column(name = "last_import_time")
    private java.time.LocalDateTime lastImportTime;

    /**
     * 数据集状态常量
     */
    public static class Status {
        public static final String IMPORTING = "importing"; // 正在导入
        public static final String UPLOADED = "uploaded";   // 已上传/导入完成
        public static final String ARCHIVED = "archived";   // 已归档
        public static final String FAILED = "failed";       // 导入失败
    }

    /**
     * 数据来源类型常量
     */
    public static class SourceType {
        public static final String FILE = "file";
        public static final String DATABASE = "database";
    }
}