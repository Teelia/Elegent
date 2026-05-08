package com.datalabeling.service.extraction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 提取结果
 * 用于表示从文本中提取出的单个号码或信息
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedNumber {

    /**
     * 提取的字段名（如：身份证号、银行卡号、手机号）
     */
    private String field;

    /**
     * 提取的值
     */
    private String value;

    /**
     * 置信度 0-1
     */
    private float confidence;

    /**
     * 验证信息（如：通过Luhn校验、识别出银行等）
     */
    private String validation;

    /**
     * 在原文中的起始位置
     */
    private Integer startIndex;

    /**
     * 在原文中的结束位置
     */
    private Integer endIndex;

    /**
     * 创建高置信度结果
     */
    public static ExtractedNumber highConfidence(String field, String value, String validation) {
        return ExtractedNumber.builder()
            .field(field)
            .value(value)
            .confidence(0.95f)
            .validation(validation)
            .build();
    }

    /**
     * 创建中等置信度结果
     */
    public static ExtractedNumber mediumConfidence(String field, String value, String validation) {
        return ExtractedNumber.builder()
            .field(field)
            .value(value)
            .confidence(0.75f)
            .validation(validation)
            .build();
    }

    /**
     * 创建低置信度结果
     */
    public static ExtractedNumber lowConfidence(String field, String value, String validation) {
        return ExtractedNumber.builder()
            .field(field)
            .value(value)
            .confidence(0.5f)
            .validation(validation)
            .build();
    }
}
