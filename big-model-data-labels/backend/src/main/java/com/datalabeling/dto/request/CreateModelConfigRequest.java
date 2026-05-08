package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 创建大模型配置请求
 */
@Data
public class CreateModelConfigRequest {

    /**
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    private String name;

    /**
     * 提供方：deepseek, qwen, openai
     */
    @NotBlank(message = "提供方不能为空")
    @Size(max = 50, message = "提供方长度不能超过50")
    private String provider;

    /**
     * API Key（可选，不传则不配置）
     */
    private String apiKey;

    /**
     * API基础URL
     */
    @NotBlank(message = "API基础URL不能为空")
    @Size(max = 255, message = "API基础URL长度不能超过255")
    private String baseUrl;

    /**
     * 模型名称
     */
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称长度不能超过100")
    private String model;

    /**
     * 超时时间（毫秒）
     */
    @NotNull(message = "超时时间不能为空")
    @Min(value = 1000, message = "超时时间至少1000毫秒")
    private Integer timeout = 30000;

    /**
     * 温度参数（0-2）
     */
    @NotNull(message = "温度参数不能为空")
    @DecimalMin(value = "0", message = "温度参数不能小于0")
    @DecimalMax(value = "2", message = "温度参数不能大于2")
    private Double temperature = 0.1;

    /**
     * 最大返回Token数
     */
    @NotNull(message = "最大Token数不能为空")
    @Min(value = 1, message = "最大Token数至少为1")
    private Integer maxTokens = 500;

    /**
     * 失败重试次数
     */
    @NotNull(message = "重试次数不能为空")
    @Min(value = 0, message = "重试次数不能为负数")
    private Integer retryTimes = 3;

    /**
     * 最大并发数（全局并发限制）
     */
    @NotNull(message = "最大并发数不能为空")
    @Min(value = 1, message = "最大并发数至少为1")
    @Max(value = 100, message = "最大并发数不能超过100")
    private Integer maxConcurrency = 10;

    /**
     * 是否激活
     */
    private Boolean isActive = true;

    /**
     * 是否设为默认
     */
    private Boolean isDefault = false;

    /**
     * 配置描述
     */
    @Size(max = 500, message = "描述长度不能超过500")
    private String description;
}
