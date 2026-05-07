package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行日志视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionLogVO {
    
    /**
     * 日志ID
     */
    private Long id;
    
    /**
     * 任务ID
     */
    private Integer taskId;
    
    /**
     * 数据行ID（可选）
     */
    private Long rowId;
    
    /**
     * 数据行号（显示用）
     */
    private Integer rowNumber;
    
    /**
     * 日志级别：INFO, WARN, ERROR
     */
    private String logLevel;
    
    /**
     * 日志级别图标
     */
    private String logLevelIcon;
    
    /**
     * 日志消息
     */
    private String message;
    
    /**
     * 详细信息
     */
    private String details;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 格式化的时间显示
     */
    private String timeDisplay;
    
    /**
     * 获取日志级别图标
     */
    public static String getLogLevelIcon(String logLevel) {
        if (logLevel == null) {
            return "ℹ️";
        }
        switch (logLevel.toUpperCase()) {
            case "INFO":
                return "✅";
            case "WARN":
                return "⚠️";
            case "ERROR":
                return "❌";
            default:
                return "ℹ️";
        }
    }
    
    /**
     * 格式化时间显示
     */
    public static String formatTimeDisplay(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}