package com.datalabeling.service.extraction;

import java.util.List;
import java.util.Map;

/**
 * 增强型提取器接口
 * 扩展了元数据、上下文感知能力，提供更精准的数据提取
 *
 * 核心增强：
 * 1. 元数据支持：提取器名称、描述、输出字段、数据类型
 * 2. 上下文感知：理解提取的数据在业务场景中的含义
 * 3. 结果增强：提取结果包含更丰富的上下文信息
 * 4. 大模型友好：提取结果可直接用于大模型分析
 */
public interface IEnhancedExtractor extends INumberExtractor {

    /**
     * 获取提取器元数据
     * 用于前端展示和用户选择
     *
     * @return 提取器元数据
     */
    ExtractorMetadata getMetadata();

    /**
     * 增强型提取方法
     * 返回带有丰富上下文信息的提取结果
     *
     * @param text 输入文本
     * @param options 提取选项
     * @return 增强型提取结果（包含上下文、业务含义等）
     */
    List<EnhancedExtractedResult> extractWithContext(String text, Map<String, Object> options);

    /**
     * 构建大模型提示词增强信息
     * 将提取结果转化为大模型可理解的结构化信息
     *
     * @param results 提取结果
     * @return 大模型提示词片段（JSON格式）
     */
    String buildLLMPromptContext(List<EnhancedExtractedResult> results);

    /**
     * 验证提取选项
     * 在提取前验证选项的合法性
     *
     * @param options 提取选项
     * @throws IllegalArgumentException 选项不合法时抛出
     */
    default void validateOptions(Map<String, Object> options) {
        // 默认不验证，子类可覆盖
    }

    /**
     * 获取默认配置选项
     * 用于前端初始化表单
     *
     * @return 默认选项
     */
    Map<String, Object> getDefaultOptions();

    /**
     * 是否支持配置项
     * 某些提取器可能不需要配置
     *
     * @return true表示支持配置
     */
    default boolean isConfigurable() {
        return true;
    }

    /**
     * 获取示例数据
     * 用于前端展示提取效果
     *
     * @return 示例数据列表（格式：[输入文本, 期望输出]）
     */
    List<ExtractorExample> getExamples();
}
