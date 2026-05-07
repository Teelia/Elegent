package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 更新标签请求DTO
 */
@Data
public class UpdateLabelRequest {

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
     * 提取字段列表（仅提取类型标签使用）
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
}
