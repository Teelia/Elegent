package com.datalabeling.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;

/**
 * 创建分析任务请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnalysisTaskRequest {
    
    /**
     * 数据集ID
     */
    @NotNull(message = "数据集ID不能为空")
    private Integer datasetId;
    
    /**
     * 任务名称（可选，默认使用数据集名称+时间戳）
     */
    @Size(max = 200, message = "任务名称不能超过200个字符")
    private String name;
    
    /**
     * 任务描述
     */
    @Size(max = 500, message = "任务描述不能超过500个字符")
    private String description;
    
    /**
     * 选择的标签ID列表
     */
    @NotEmpty(message = "至少选择一个标签")
    private List<Integer> labelIds;
    
    /**
     * 默认信心度阈值（0-1之间）
     */
    @DecimalMin(value = "0.0", message = "信心度阈值不能小于0")
    @DecimalMax(value = "1.0", message = "信心度阈值不能大于1")
    private BigDecimal defaultConfidenceThreshold;
    
    /**
     * 是否立即开始执行
     */
    private Boolean autoStart;

    /**
     * 指定的模型配置ID（可选，不指定则使用默认配置）
     */
    private Integer modelConfigId;

    /**
     * 并发处理数量（同时处理多少条数据，1-10）
     */
    @Min(value = 1, message = "并发数不能小于1")
    @Max(value = 10, message = "并发数不能大于10")
    private Integer concurrency;
}