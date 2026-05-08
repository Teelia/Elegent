package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 创建标签请求DTO
 */
@Data
public class CreateLabelRequest {

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
    private String name;

    /**
     * 标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取)
     */
    @Size(max = 30, message = "标签类型长度不能超过30")
    private String type;

    /**
     * 标签描述
     */
    @NotBlank(message = "标签描述不能为空")
    private String description;

    /**
     * 重点关注列
     */
    private List<String> focusColumns;

    /**
     * 提取字段列表（type=extraction或structured_extraction时需要）
     */
    private List<String> extractFields;

    /**
     * 提取器配置（仅type=structured_extraction时需要）
     * JSON格式字符串，包含提取器类型和选项
     */
    private String extractorConfig;

    /**
     * 预处理模式（适用于 classification 和 extraction 类型）
     * llm_only: 仅使用 LLM，不使用规则提取器
     * rule_only: 仅使用规则提取器，不调用 LLM
     * rule_then_llm: 先用规则提取器预处理，然后将结果传给 LLM
     */
    @Size(max = 20, message = "预处理模式长度不能超过20")
    private String preprocessingMode;

    /**
     * 预处理器配置（JSON格式，当preprocessingMode=rule_only或rule_then_llm时有效）
     * 可扩展保存“号码类标签意图(number_intent)”等配置。
     */
    private String preprocessorConfig;

    /**
     * 是否将预处理结果传入 LLM（仅 rule_then_llm 模式有效）
     */
    private Boolean includePreprocessorInPrompt;

    /**
     * 是否启用二次强化分析
     */
    private Boolean enableEnhancement;

    /**
     * 强化分析配置（JSON格式，当enableEnhancement=true时有效）
     */
    private String enhancementConfig;

    /**
     * 作用域：global（全局）, dataset（数据集专属）
     * 默认为 global
     */
    @Size(max = 20, message = "作用域长度不能超过20")
    private String scope;

    /**
     * 关联数据集ID（仅scope=dataset时需要）
     */
    private Integer datasetId;
}
