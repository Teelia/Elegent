package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务进度视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressVO {

    /**
     * 任务ID
     */
    private Integer taskId;

    /**
     * 总行数
     */
    private Integer total;

    /**
     * 已处理行数
     */
    private Integer processed;

    /**
     * 失败行数
     */
    private Integer failed;

    /**
     * 进度百分比
     */
    private Integer percentage;

    /**
     * 当前处理的行号
     */
    private Integer currentRow;

    /**
     * 预计剩余时间（秒）
     */
    private Integer etaSeconds;

    /**
     * 状态
     */
    private String status;
}
