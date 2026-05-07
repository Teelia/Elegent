package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 批量更新数据行请求DTO
 */
@Data
public class BatchUpdateDataRowsRequest {

    /**
     * 批量更新项
     */
    @NotNull(message = "更新项不能为空")
    @NotEmpty(message = "更新项不能为空")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {

        @NotNull(message = "rowId 不能为空")
        private Long rowId;

        @NotNull(message = "标签结果不能为空")
        private Map<String, Object> labelResults;
    }
}

