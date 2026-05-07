package com.datalabeling.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * OkHttp客户端配置
 */
@Configuration
public class OkHttpConfig {

    @Value("${deepseek.timeout:30000}")
    private int timeout;

    /**
     * 配置OkHttpClient
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
            // 连接超时
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            // 读取超时
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            // 写入超时
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            // 连接池配置
            .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
            // 失败重试
            .retryOnConnectionFailure(true)
            .build();
    }
}
