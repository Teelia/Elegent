package com.datalabeling.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 提取器配置实体
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"patterns", "options"})
@Entity
@Table(name = "extractor_configs")
public class ExtractorConfig extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 创建用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 提取器名称
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * 提取器代码（唯一标识）
     */
    @EqualsAndHashCode.Include
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 提取器描述
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 分类：builtin(内置), custom(自定义)
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category = "custom";

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 是否系统内置（系统内置不可删除）
     */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    /**
     * 关联的正则规则
     */
    @OneToMany(mappedBy = "extractor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, priority DESC")
    private Set<ExtractorPattern> patterns = new HashSet<>();

    /**
     * 关联的选项配置
     */
    @OneToMany(mappedBy = "extractor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private Set<ExtractorOption> options = new HashSet<>();

    /**
     * 分类常量
     */
    public static class Category {
        public static final String BUILTIN = "builtin";
        public static final String CUSTOM = "custom";
    }
}