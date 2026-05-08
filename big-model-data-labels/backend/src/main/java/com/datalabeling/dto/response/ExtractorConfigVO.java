package com.datalabeling.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提取器配置VO
 */
@Data
public class ExtractorConfigVO {

    private Integer id;

    /**
     * 创建用户ID
     */
    private Integer userId;

    /**
     * 提取器名称
     */
    private String name;

    /**
     * 提取器代码
     */
    private String code;

    /**
     * 提取器描述
     */
    private String description;

    /**
     * 分类
     */
    private String category;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 是否系统内置
     */
    private Boolean isSystem;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 正则规则列表
     */
    private List<PatternVO> patterns;

    /**
     * 选项配置列表
     */
    private List<OptionVO> options;

    /**
     * 正则规则VO
     */
    @Data
    public static class PatternVO {
        private Integer id;
        private String name;
        private String pattern;
        private String description;
        private Integer priority;
        private BigDecimal confidence;
        private String validationType;
        private String validationConfig;
        private Boolean isActive;
        private Integer sortOrder;
    }

    /**
     * 选项配置VO
     */
    @Data
    public static class OptionVO {
        private Integer id;
        private String optionKey;
        private String optionName;
        private String optionType;
        private String defaultValue;
        private String description;
        private String selectOptions;
        private Integer sortOrder;
    }
}