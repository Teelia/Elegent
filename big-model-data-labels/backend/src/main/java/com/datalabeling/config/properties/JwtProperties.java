package com.datalabeling.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT签名密钥
     */
    private String secret;

    /**
     * Token过期时间（毫秒）
     */
    private Long expiration = 3600000L;

    /**
     * Token请求头名称
     */
    private String header = "Authorization";

    /**
     * Token前缀
     */
    private String prefix = "Bearer ";
}
