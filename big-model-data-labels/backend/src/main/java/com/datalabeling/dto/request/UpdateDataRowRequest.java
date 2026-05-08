package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 更新数据行请求DTO
 */
@Data
public class UpdateDataRowRequest {

    /**
     * 标签结果
     * 格式：{"标签名_v1": "是", "标签名_v2": "否"}
     */
    @NotNull(message = "标签结果不能为空")
    private Map<String, Object> labelResults;
}
