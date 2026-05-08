package com.datalabeling.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 大模型配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "model_configs", indexes = {
    @Index(name = "idx_model_configs_provider", columnList = "provider"),
    @Index(name = "idx_model_configs_active", columnList = "is_active"),
    @Index(name = "idx_model_configs_default", columnList = "is_default")
})
public class ModelConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 配置名称（用于显示）
     */
    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 提供方：deepseek, qwen, openai等
     */
    @NotBlank(message = "provider不能为空")
    @Size(max = 50, message = "provider长度不能超过50")
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * 加密后的API Key
     */
    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    /**
     * API基础URL
     */
    @NotBlank(message = "baseUrl不能为空")
    @Size(max = 255, message = "baseUrl长度不能超过255")
    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    /**
     * 模型名称
     */
    @NotBlank(message = "model不能为空")
    @Size(max = 100, message = "model长度不能超过100")
    @Column(nullable = false, length = 100)
    private String model;

    /**
     * 超时时间（毫秒）
     */
    @NotNull(message = "timeout不能为空")
    @Min(value = 1000, message = "timeout至少1000毫秒")
    @Column(nullable = false)
    private Integer timeout = 30000;

    /**
     * 温度参数（0-2）
     */
    @NotNull(message = "temperature不能为空")
    @Min(value = 0, message = "temperature不能小于0")
    @Max(value = 2, message = "temperature不能大于2")
    @Column(nullable = false)
    private Double temperature = 0.1;

    /**
     * 最大返回Token数
     */
    @NotNull(message = "maxTokens不能为空")
    @Min(value = 1, message = "maxTokens至少为1")
    @Column(name = "max_tokens", nullable = false)
    private Integer maxTokens = 500;

    /**
     * 失败重试次数
     */
    @NotNull(message = "retryTimes不能为空")
    @Min(value = 0, message = "retryTimes不能为负数")
    @Column(name = "retry_times", nullable = false)
    private Integer retryTimes = 3;

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 是否为默认配置
     */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    /**
     * 最大并发数（全局并发限制）
     */
    @Min(value = 1, message = "最大并发数至少为1")
    @Max(value = 100, message = "最大并发数不能超过100")
    @Column(name = "max_concurrency", nullable = false)
    private Integer maxConcurrency = 10;

    /**
     * 配置描述
     */
    @Size(max = 500, message = "描述长度不能超过500")
    @Column(length = 500)
    private String description;

    /**
     * 提供方常量
     */
    public static class Provider {
        public static final String DEEPSEEK = "deepseek";
        public static final String LOCAL_DEEPSEEK = "local-deepseek";
        public static final String QWEN = "qwen";
        public static final String OPENAI = "openai";
    }

    /**
     * 获取提供方显示名称
     */
    public String getProviderDisplayName() {
        switch (provider) {
            case Provider.DEEPSEEK:
                return "DeepSeek";
            case Provider.LOCAL_DEEPSEEK:
                return "本地 DeepSeek (自部署)";
            case Provider.QWEN:
                return "通义千问 (Qwen)";
            case Provider.OPENAI:
                return "OpenAI";
            default:
                return provider;
        }
    }
}

