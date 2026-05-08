package com.datalabeling.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 文件上传配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /**
     * 文件上传目录
     */
    private String uploadDir = "./uploads";

    /**
     * 文件最大大小（字节）
     */
    private Long maxSize = 52428800L; // 50MB

    /**
     * 允许的文件扩展名
     */
    private List<String> allowedExtensions = Arrays.asList(".xlsx", ".xls", ".csv");
}
