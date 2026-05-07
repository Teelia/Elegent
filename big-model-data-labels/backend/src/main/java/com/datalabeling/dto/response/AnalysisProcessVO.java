package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Analysis process view object
 * Used for frontend polling to display analysis progress and AI conversation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisProcessVO {
    
    private Integer taskId;
    private String taskName;
    private String status;
    private String statusDisplay;
    private Integer totalRows;
    private Integer processedRows;
    private Integer successRows;
    private Integer failedRows;
    private Integer progressPercent;
    private Integer estimatedSecondsRemaining;
    private List<AnalyzingLabel> analyzingLabels;
    private List<AnalysisLogEntry> recentLogs;
    private CurrentProcessingInfo currentProcessing;
    private LocalDateTime startedAt;
    private LocalDateTime lastUpdatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyzingLabel {
        private Integer labelId;
        private String labelName;
        private Integer labelVersion;
        private String labelDescription;
        private Integer processedRows;
        private Long hitCount;
        private Double hitRate;
        private Boolean isProcessing;
        private String processingStatus;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisLogEntry {
        private Long id;
        private Long dataRowId;
        private Integer rowIndex;
        private String labelKey;
        private String logLevel;
        private String message;
        private BigDecimal confidence;
        private Integer durationMs;
        private LocalDateTime createdAt;
        private String timeDisplay;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentProcessingInfo {
        private Integer currentRowIndex;
        private String currentLabelName;
        private String processingPhase;
        private String processingPhaseDisplay;
    }
    
    public static String getProcessingPhaseDisplay(String phase) {
        if (phase == null) {
            return "Processing";
        }
        switch (phase) {
            case "sending_request":
                return "Sending request to AI";
            case "waiting_response":
                return "Waiting for AI response";
            case "parsing_result":
                return "Parsing analysis result";
            default:
                return "Processing";
        }
    }
}