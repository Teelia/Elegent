package com.datalabeling.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签视图对象
 */
@Data
public class LabelVO {

    /**
     * 标签ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 作用域：global, dataset, task
     */
    private String scope;

    /**
     * 标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取)
     */
    private String type;

    /**
     * 关联任务ID（仅scope=task时有效）
     */
    private Integer taskId;

    /**
     * 标签描述
     */
    private String description;

    /**
     * 重点关注列
     */
    private List<String> focusColumns;

    /**
     * 提取字段列表（仅type=extraction或structured_extraction时有效）
     */
    private List<String> extractFields;

    /**
     * 提取器配置（仅type=structured_extraction时有效）
     * JSON格式字符串，包含提取器类型和选项
     */
    private String extractorConfig;

    /**
     * 预处理模式（适用于 classification 和 extraction 类型）
     * llm_only: 仅使用 LLM，不使用规则提取器
     * rule_only: 仅使用规则提取器，不调用 LLM
     * rule_then_llm: 先用规则提取器预处理，然后将结果传给 LLM
     */
    private String preprocessingMode;

    /**
     * 预处理器配置（JSON格式，当preprocessingMode=rule_only或rule_then_llm时有效）
     * 包含extractors列表和各提取器的选项
     */
    private String preprocessorConfig;

    /**
     * 是否将预处理结果传入 LLM（仅 rule_then_llm 模式有效）
     * true: 预处理结果会作为上下文传给 LLM，帮助提高判断准确性
     * false: 预处理结果仅用于内部逻辑，不传给 LLM
     */
    private Boolean includePreprocessorInPrompt;

    /**
     * 是否启用二次强化分析
     */
    private Boolean enableEnhancement;

    /**
     * 强化分析配置（JSON格式，当enableEnhancement=true时有效）
     * 包含触发条件、提示词ID等配置
     */
    private String enhancementConfig;

    /**
     * 关联数据集ID（仅scope=dataset时有效）
     */
    private Integer datasetId;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 内置级别：system / custom
     */
    private String builtinLevel;

    /**
     * 内置分类
     */
    private String builtinCategory;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
