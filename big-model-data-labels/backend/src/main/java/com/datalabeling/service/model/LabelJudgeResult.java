package com.datalabeling.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 标签判断结果（包含信心度）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelJudgeResult {
    
    /**
     * 判断结果：是/否
     */
    private String result;
    
    /**
     * AI信心度（0-100）
     */
    private BigDecimal confidence;
    
    /**
     * AI分析原因（可选）
     */
    private String reason;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long durationMs;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
    
    /**
     * 创建成功结果
     */
    public static LabelJudgeResult success(String result, BigDecimal confidence, String reason, Long durationMs) {
        return LabelJudgeResult.builder()
                .result(result)
                .confidence(confidence)
                .reason(reason)
                .durationMs(durationMs)
                .success(true)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static LabelJudgeResult failure(String errorMessage, Long durationMs) {
        return LabelJudgeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .build();
    }
}