package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 数据库同步请求DTO
 */
@Data
public class SyncToDbRequest {

    /**
     * 同步配置ID
     */
    @NotNull(message = "同步配置ID不能为空")
    private Integer syncConfigId;

    /**
     * 字段映射
     * 格式：{"文件列名": "数据库字段名"}
     */
    @NotNull(message = "字段映射不能为空")
    @NotEmpty(message = "字段映射不能为空")
    private Map<String, String> fieldMappings;

    /**
     * 同步策略：insert, update, replace
     */
    @NotBlank(message = "同步策略不能为空")
    private String strategy;
}
