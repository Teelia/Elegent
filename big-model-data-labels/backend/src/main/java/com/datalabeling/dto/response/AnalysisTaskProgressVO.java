package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析任务进度视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskProgressVO {

    private Integer taskId;
    private String status;
    private Integer total;
    private Integer processed;
    private Integer success;
    private Integer failed;
    private Integer percentage;
    private Integer etaSeconds;
    private List<LabelProgressItem> labelProgress;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LabelProgressItem {
        private Integer labelId;
        private String labelName;
        private Integer processed;
        private Integer total;
        private Integer percentage;
    }
}
