package com.datalabeling.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;

/**
 * 提取器选项配置实体
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"extractor"})
@Entity
@Table(name = "extractor_options")
public class ExtractorOption extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 关联的提取器
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extractor_id", nullable = false)
    private ExtractorConfig extractor;

    /**
     * 选项键
     */
    @Column(name = "option_key", nullable = false, length = 50)
    private String optionKey;

    /**
     * 选项名称
     */
    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    /**
     * 选项类型：boolean, string, number, select
     */
    @Column(name = "option_type", nullable = false, length = 20)
    private String optionType = "boolean";

    /**
     * 默认值
     */
    @Column(name = "default_value", length = 255)
    private String defaultValue;

    /**
     * 选项描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 下拉选项（仅type=select时有效）
     */
    @Column(name = "select_options", columnDefinition = "JSON")
    private String selectOptions;

    /**
     * 排序顺序
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 选项类型常量
     */
    public static class OptionType {
        public static final String BOOLEAN = "boolean";
        public static final String STRING = "string";
        public static final String NUMBER = "number";
        public static final String SELECT = "select";
    }
}