package com.datalabeling.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * 提取器正则规则实体
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"extractor"})
@Entity
@Table(name = "extractor_patterns")
public class ExtractorPattern extends BaseEntity {

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
     * 规则名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 正则表达式
     */
    @Column(name = "pattern", nullable = false, columnDefinition = "TEXT")
    private String pattern;

    /**
     * 规则描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 优先级（数字越大优先级越高）
     */
    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    /**
     * 匹配时的默认信心度
     */
    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence = new BigDecimal("0.90");

    /**
     * 验证类型：checksum(校验位), luhn(Luhn算法), none(无)
     */
    @Column(name = "validation_type", length = 50)
    private String validationType;

    /**
     * 验证配置（JSON格式）
     */
    @Column(name = "validation_config", columnDefinition = "JSON")
    private String validationConfig;

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 排序顺序
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 验证类型常量
     */
    public static class ValidationType {
        public static final String CHECKSUM = "checksum";
        public static final String LUHN = "luhn";
        public static final String NONE = "none";
    }
}