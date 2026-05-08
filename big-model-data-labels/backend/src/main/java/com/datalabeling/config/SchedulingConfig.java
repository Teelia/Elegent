package com.datalabeling.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * 启用 Spring 的 @Scheduled 注解支持
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 无需额外配置，@EnableScheduling 注解已启用定时任务支持
}
