package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据库浏览器响应
 * 用于返回外部数据源中的数据库和表列表
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseExplorerVO {

    /**
     * 数据库列表
     */
    private List<DatabaseItem> databases;

    /**
     * 数据库项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatabaseItem {
        /**
         * 数据库名称（Oracle 中为 Schema 名称）
         */
        private String name;

        /**
         * 表列表
         */
        private List<TableItem> tables;
    }

    /**
     * 表项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableItem {
        /**
         * 表名
         */
        private String name;

        /**
         * 类型：TABLE, VIEW
         */
        private String type;

        /**
         * 估算的行数（可选）
         */
        private Long rowCount;

        /**
         * 表注释（可选）
         */
        private String comment;
    }
}
