package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 目标表结构信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableSchemaVO {

    private List<Column> columns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        private String name;
        private String type;
        private Boolean nullable;
    }
}

