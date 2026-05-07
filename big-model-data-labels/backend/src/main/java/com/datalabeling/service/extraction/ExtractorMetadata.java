package com.datalabeling.service.extraction;

import lombok.Builder;
import lombok.Data;

/**
 * 提取器元数据
 * 描述提取器的功能、配置、输出等信息
 */
@Data
@Builder
public class ExtractorMetadata {

    /**
     * 提取器代码（唯一标识）
     */
    private String code;

    /**
     * 提取器名称
     */
    private String name;

    /**
     * 提取器描述
     */
    private String description;

    /**
     * 提取器类别（builtin/custom）
     */
    private String category;

    /**
     * 输出字段名称
     */
    private String outputField;

    /**
     * 输出数据类型（string/number/date/json/array）
     */
    private String dataType;

    /**
     * 是否返回多个值
     */
    private boolean multiValue;

    /**
     * 支持的配置项
     */
    private java.util.List<ExtractorOption> options;

    /**
     * 标签（用于分类和搜索）
     */
    private java.util.List<String> tags;

    /**
     * 适用场景
     */
    private String useCase;

    /**
     * 准确度评级（high/medium/low）
     */
    private String accuracy;

    /**
     * 性能评级（fast/medium/slow）
     */
    private String performance;

    /**
     * 版本号
     */
    private String version;

    /**
     * 作者
     */
    private String author;

    /**
     * 配置项定义
     */
    @Data
    @Builder
    public static class ExtractorOption {
        private String key;
        private String name;
        private String description;
        private String type; // boolean/string/number/select
        private Object defaultValue;
        private java.util.List<String> selectOptions; // 仅type=select时使用
        private boolean required;
    }
}
