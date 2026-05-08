package com.datalabeling.service.model;

import com.datalabeling.entity.DataRow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文件解析结果
 * 包含列信息、预览数据和所有数据行（用于存入数据库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilePreviewResult {

    /**
     * 列名（按文件顺序）
     */
    private List<String> columns;

    /**
     * 列信息（JSON格式，用于存储到数据库）
     * 格式：[{"index":0,"name":"列名","dataType":"文本","nonNullRate":100}]
     */
    private List<Map<String, Object>> columnsInfo;

    /**
     * 预览数据（前N行）
     */
    private List<Map<String, Object>> preview;

    /**
     * 数据总行数（不含表头）
     */
    private Integer totalRows;

    /**
     * 解析出的数据行列表（用于批量存入数据库）
     */
    private List<DataRow> dataRows;
}

