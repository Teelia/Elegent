package com.datalabeling.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Size;

/**
 * 创建数据集请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDatasetRequest {
    
    /**
     * 数据集描述（可选）
     */
    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}