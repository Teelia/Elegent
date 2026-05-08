package com.datalabeling.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 二次强化分析配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancementConfig {

    /**
     * 触发条件（旧格式，已弃用）
     * always: 总是启用
     * confidence_below_90: 置信度低于90%时触发
     * confidence_below_95: 置信度低于95%时触发
     * confidence_below_100: 置信度低于100%时触发（即只要不是100%就触发）
     * @deprecated 使用 triggerConfidence 代替
     */
    @Deprecated
    @JsonProperty("triggerCondition")
    private String triggerCondition;

    /**
     * 触发阈值：置信度低于此值时触发强化（新格式）
     * 例如：70 表示置信度低于 70% 时触发
     * 如果为 null，则使用默认值 70
     */
    @JsonProperty("triggerConfidence")
    @Builder.Default
    private Integer triggerConfidence = 70;

    /**
     * 自定义提示词ID（可选，如果为null则使用系统默认）
     */
    @JsonProperty("promptId")
    private Integer promptId;

    /**
     * 旧字段名兼容（已弃用）
     * @deprecated 使用 promptId 代替
     */
    @Deprecated
    @JsonProperty("enhancementPromptId")
    private Integer enhancementPromptId;

    /**
     * 从JSON字符串解析配置
     */
    public static EnhancementConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            // 返回默认配置
            return EnhancementConfig.builder().build();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            EnhancementConfig config = mapper.readValue(json, EnhancementConfig.class);

            // 向后兼容：如果使用旧字段，转换为新字段
            if (config.triggerCondition != null && config.triggerConfidence == null) {
                config.triggerConfidence = parseOldTriggerCondition(config.triggerCondition);
            }

            // 兼容旧的 promptId 字段名
            if (config.enhancementPromptId != null && config.promptId == null) {
                config.promptId = config.enhancementPromptId;
            }

            return config;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析强化配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("转换强化配置为JSON失败: " + e.getMessage(), e);
        }
    }

    /**
     * 判断是否应该触发强化分析（基础版本）
     */
    public boolean shouldTrigger(int initialConfidence) {
        int threshold = triggerConfidence != null ? triggerConfidence : 70;
        return initialConfidence < threshold;
    }

    /**
     * 判断是否应该触发强化分析（增强版本，支持强制触发）
     *
     * @param initialConfidence 初始置信度
     * @param validationResult 规则验证结果（可选）
     * @return 是否应该触发二次强化
     */
    public boolean shouldTrigger(int initialConfidence, String validationResult) {
        // 基础触发条件：置信度低于阈值
        if (shouldTrigger(initialConfidence)) {
            return true;
        }

        // ========== L4: 强制触发条件 ==========
        // 即使置信度高于阈值，以下情况仍强制触发二次验证

        if (validationResult != null && !validationResult.trim().isEmpty()) {
            // 强制触发条件1: 存在格式错误的身份证号
            if (containsAny(validationResult,
                "格式错误", "长度错误",
                "17位", "19位", "15位",
                "非18位", "非标准")) {
                return true;
            }

            // 强制触发条件2: 存在信息缺失关键词
            if (containsAny(validationResult,
                "拒绝", "拒绝登记", "拒绝提供", "拒不透露",
                "未提供", "未登记", "无法", "无法登记",
                "研判无果", "无果", "不详", "记不清")) {
                return true;
            }

            // 强制触发条件3: 存在当事人信息缺失
            if (containsAny(validationResult,
                "当事人.*缺失", "信息不完整",
                "未提取到", "未找到",
                "隐含.*当事人", "推断.*当事人")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查字符串是否包含任意一个关键词
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取提示词ID（兼容新旧字段）
     */
    public Integer getEnhancementPromptId() {
        return promptId != null ? promptId : enhancementPromptId;
    }

    /**
     * 解析旧的触发条件字符串，转换为阈值
     */
    private static Integer parseOldTriggerCondition(String triggerCondition) {
        if (triggerCondition == null) {
            return 70;
        }
        if ("always".equals(triggerCondition)) {
            return 0;  // 总是触发
        } else if (triggerCondition.startsWith("confidence_below_")) {
            try {
                String numStr = triggerCondition.replace("confidence_below_", "");
                return Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                return 70;  // 默认值
            }
        }
        return 70;  // 默认值
    }
}
