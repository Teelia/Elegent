package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 任务统计视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatisticsVO {

    /**
     * 任务ID
     */
    private Integer taskId;

    /**
     * 总行数
     */
    private Integer totalRows;

    /**
     * 已处理行数
     */
    private Integer processedRows;

    /**
     * 失败行数
     */
    private Integer failedRows;

    /**
     * 标签统计
     * 格式：{"标签名_v1": {"是": 100, "否": 200}}
     */
    private Map<String, Map<String, Integer>> labelStatistics;

    /**
     * 标签分布数据（用于图表）
     */
    private List<LabelDistribution> labelDistributions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelDistribution {
        /**
         * 标签名称
         */
        private String labelName;

        /**
         * 标签值
         */
        private String labelValue;

        /**
         * 数量
         */
        private Integer count;

        /**
         * 百分比
         */
        private Double percentage;
    }
}
