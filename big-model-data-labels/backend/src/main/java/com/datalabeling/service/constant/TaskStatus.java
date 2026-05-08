package com.datalabeling.service.constant;

/**
 * 任务状态常量
 */
public final class TaskStatus {

    private TaskStatus() {
    }

    public static final String UPLOADED = "uploaded";
    public static final String PENDING = "pending";      // 待启动（已配置标签，等待手动启动）
    public static final String PROCESSING = "processing";
    public static final String PAUSED = "paused";        // 已暂停
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";
    public static final String CANCELLED = "cancelled";  // 已取消
    public static final String ARCHIVED = "archived";
    
    /**
     * 判断是否可以配置标签
     */
    public static boolean canConfigureLabels(String status) {
        return UPLOADED.equals(status) || PENDING.equals(status);
    }
    
    /**
     * 判断是否可以启动分析
     */
    public static boolean canStart(String status) {
        return PENDING.equals(status) || PAUSED.equals(status);
    }
    
    /**
     * 判断是否正在处理中
     */
    public static boolean isProcessing(String status) {
        return PROCESSING.equals(status);
    }
    
    /**
     * 判断是否已完成（包括成功、失败、取消）
     */
    public static boolean isFinished(String status) {
        return COMPLETED.equals(status) || FAILED.equals(status) || CANCELLED.equals(status);
    }
}

