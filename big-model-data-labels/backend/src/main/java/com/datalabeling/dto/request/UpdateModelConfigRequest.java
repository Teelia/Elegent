package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * 更新大模型配置请求（管理员）
 */
@Data
public class UpdateModelConfigRequest {

    /**
     * 配置名称
     */
    @Size(max = 100, message = "配置名称长度不能超过100")
    private String name;

    /**
     * API Key（可选；留空表示不修改）
     */
    @Size(max = 500, message = "apiKey长度不能超过500")
    private String apiKey;

    /**
     * 是否清空 API Key（优先级高于 apiKey）
     */
    private Boolean clearApiKey;

    @Size(max = 255, message = "baseUrl长度不能超过255")
    private String baseUrl;

    @Size(max = 100, message = "model长度不能超过100")
    private String model;

    @Min(value = 1000, message = "timeout至少1000毫秒")
    private Integer timeout;

    @DecimalMin(value = "0", message = "temperature不能小于0")
    @DecimalMax(value = "2", message = "temperature不能大于2")
    private Double temperature;

    @Min(value = 1, message = "maxTokens至少为1")
    private Integer maxTokens;

    @Min(value = 0, message = "retryTimes不能为负数")
    private Integer retryTimes;

    @Min(value = 1, message = "maxConcurrency至少为1")
    @Max(value = 100, message = "maxConcurrency不能超过100")
    private Integer maxConcurrency;

    private Boolean isActive;

    /**
     * 是否设为默认配置
     */
    private Boolean isDefault;

    /**
     * 配置描述
     */
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;
}

