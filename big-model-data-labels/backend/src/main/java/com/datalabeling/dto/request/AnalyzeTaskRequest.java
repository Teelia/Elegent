package com.datalabeling.dto.request;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 分析任务请求DTO
 */
@Data
public class AnalyzeTaskRequest {

    /**
     * 标签ID列表
     */
    @NotNull(message = "标签ID列表不能为空")
    @NotEmpty(message = "至少选择一个标签")
    private List<Integer> labelIds;

    /**
     * 指定的模型配置ID
     */
    private Integer modelConfigId;

    /**
     * 是否包含推理过程
     */
    private Boolean includeReasoning = false;
}
