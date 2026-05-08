package com.datalabeling.service.extraction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 提取器示例
 * 用于展示提取器的输入输出示例
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractorExample {

    /**
     * 示例输入
     */
    private String input;

    /**
     * 期望输出（JSON格式）
     */
    private String expectedOutput;

    /**
     * 示例说明
     */
    private String description;

    /**
     * 创建简单示例
     */
    public static ExtractorExample of(String input, String expectedOutput, String description) {
        return ExtractorExample.builder()
            .input(input)
            .expectedOutput(expectedOutput)
            .description(description)
            .build();
    }

    /**
     * 创建批量示例
     */
    public static List<ExtractorExample> batchOf(Object... examples) {
        return java.util.Arrays.stream(examples)
            .map(obj -> (ExtractorExample) obj)
            .collect(java.util.stream.Collectors.toList());
    }
}
