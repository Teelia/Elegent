package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * AI生成提取器请求
 */
@Data
public class AiGenerateExtractorRequest {

    /**
     * 生成模式：description(描述式), samples(示例式)
     */
    @NotNull(message = "生成模式不能为空")
    private String mode;

    /**
     * 提取器名称
     */
    @NotEmpty(message = "提取器名称不能为空")
    private String extractorName;

    /**
     * 描述内容（描述式模式必填）
     */
    private String description;

    /**
     * 示例数据（示例式模式必填），多个示例用换行分隔
     */
    private String samples;

    /**
     * 是否需要验证规则
     */
    private Boolean needValidation = false;

    /**
     * 生成模式常量
     */
    public static class Mode {
        public static final String DESCRIPTION = "description";
        public static final String SAMPLES = "samples";
    }
}
