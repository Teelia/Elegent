package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文件上传响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    /**
     * 任务ID
     */
    private Integer taskId;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 总行数
     */
    private Integer totalRows;

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 预览数据（前20行）
     */
    private List<Map<String, Object>> preview;
}
