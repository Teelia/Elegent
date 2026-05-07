package com.datalabeling.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek API配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API基础URL
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * 使用的模型
     */
    private String model = "deepseek-chat";

    /**
     * 超时时间（毫秒）
     */
    private Integer timeout = 30000;

    /**
     * 温度参数（0-2，越低越确定性）
     */
    private Double temperature = 0.1;

    /**
     * 最大返回Token数
     */
    private Integer maxTokens = 10;

    /**
     * 失败重试次数
     */
    private Integer retryTimes = 3;
}
