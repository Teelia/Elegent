package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 表数据预览响应
 * 用于返回外部数据源的表数据预览
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TablePreviewVO {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 列信息（列名列表）
     */
    private List<String> columns;

    /**
     * 数据行（前20行）
     */
    private List<Map<String, Object>> rows;

    /**
     * 总行数（估算）
     */
    private Long totalRows;

    /**
     * 实际执行的 SQL 语句
     */
    private String sql;
}
