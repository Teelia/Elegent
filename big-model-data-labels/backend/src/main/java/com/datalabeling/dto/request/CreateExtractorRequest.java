package com.datalabeling.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建提取器请求
 */
@Data
public class CreateExtractorRequest {

    /**
     * 提取器名称
     */
    private String name;

    /**
     * 提取器代码（唯一标识）
     */
    private String code;

    /**
     * 提取器描述
     */
    private String description;

    /**
     * 正则规则列表
     */
    private List<PatternRequest> patterns;

    /**
     * 选项配置列表
     */
    private List<OptionRequest> options;

    /**
     * 正则规则请求
     */
    @Data
    public static class PatternRequest {
        /**
         * 规则名称
         */
        private String name;

        /**
         * 正则表达式
         */
        private String pattern;

        /**
         * 规则描述
         */
        private String description;

        /**
         * 优先级
         */
        private Integer priority = 0;

        /**
         * 信心度
         */
        private BigDecimal confidence = new BigDecimal("0.90");

        /**
         * 验证类型
         */
        private String validationType;

        /**
         * 验证配置
         */
        private String validationConfig;

        /**
         * 是否激活
         */
        private Boolean isActive = true;

        /**
         * 排序顺序
         */
        private Integer sortOrder = 0;
    }

    /**
     * 选项配置请求
     */
    @Data
    public static class OptionRequest {
        /**
         * 选项键
         */
        private String optionKey;

        /**
         * 选项名称
         */
        private String optionName;

        /**
         * 选项类型
         */
        private String optionType = "boolean";

        /**
         * 默认值
         */
        private String defaultValue;

        /**
         * 选项描述
         */
        private String description;

        /**
         * 下拉选项
         */
        private String selectOptions;

        /**
         * 排序顺序
         */
        private Integer sortOrder = 0;
    }
}