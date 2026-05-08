package com.datalabeling.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 大模型配置视图对象（管理员）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigVO {

    private Integer id;

    /**
     * 配置名称
     */
    private String name;

    /**
     * 提供方：deepseek, qwen, openai
     */
    private String provider;

    /**
     * 提供方显示名称
     */
    private String providerDisplayName;

    private String baseUrl;

    private String model;

    private Integer timeout;

    private Double temperature;

    private Integer maxTokens;

    private Integer retryTimes;

    /**
     * 最大并发数（全局并发限制）
     */
    private Integer maxConcurrency;

    /**
     * 当前并发数（运行时）
     */
    private Integer currentConcurrency;

    private Boolean isActive;

    /**
     * 是否为默认配置
     */
    private Boolean isDefault;

    /**
     * 配置描述
     */
    private String description;

    /**
     * API Key 是否已配置（不返回明文）
     */
    private Boolean apiKeyConfigured;

    /**
     * 当前配置是否来自数据库（否则来自 application.yml）
     */
    private Boolean fromDb;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}

