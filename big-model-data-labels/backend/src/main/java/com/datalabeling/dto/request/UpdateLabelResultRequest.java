package com.datalabeling.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 更新标签结果请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLabelResultRequest {

    /**
     * 结果值（分类标签为是/否，提取标签为摘要）
     */
    private String resultValue;

    /**
     * 结果值（前端使用 result 字段名）
     */
    private String result;

    /**
     * 信心度阈值（0-1之间）
     */
    @DecimalMin(value = "0.0", message = "信心度阈值不能小于0")
    @DecimalMax(value = "1.0", message = "信心度阈值不能大于1")
    private BigDecimal confidenceThreshold;

    /**
     * 提取的数据（仅提取类型标签使用）
     */
    private Map<String, Object> extractedData;

    /**
     * 获取实际的结果值（兼容 resultValue 和 result 两种字段名）
     */
    public String getActualResult() {
        return result != null ? result : resultValue;
    }
}