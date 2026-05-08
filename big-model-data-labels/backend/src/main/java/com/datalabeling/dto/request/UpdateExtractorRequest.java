package com.datalabeling.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 更新提取器请求
 */
@Data
public class UpdateExtractorRequest {

    /**
     * 提取器名称
     */
    private String name;

    /**
     * 提取器描述
     */
    private String description;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 正则规则列表（完整替换）
     */
    private List<CreateExtractorRequest.PatternRequest> patterns;

    /**
     * 选项配置列表（完整替换）
     */
    private List<CreateExtractorRequest.OptionRequest> options;
}