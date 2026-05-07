package com.datalabeling.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据集视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatasetVO {

    /**
     * 数据集ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 原始文件名（上传时的文件名）
     */
    private String originalFilename;

    /**
     * 总行数
     */
    private Integer totalRows;

    /**
     * 列信息
     */
    private List<ColumnInfo> columns;

    /**
     * 状态：uploaded, archived
     */
    private String status;

    /**
     * 状态显示名称
     */
    private String statusDisplay;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 关联的分析任务数量
     */
    private Integer taskCount;

    /**
     * 最新分析任务状态
     */
    private String latestTaskStatus;

    /**
     * 最新分析任务进度
     */
    private Integer latestTaskProgress;

    /**
     * 数据集专属标签数量
     */
    private Integer datasetLabelCount;
    
    /**
     * 列信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        /**
         * 列索引
         */
        private Integer index;
        
        /**
         * 列名
         */
        private String name;
        
        /**
         * 数据类型
         */
        private String dataType;
        
        /**
         * 非空率
         */
        private Double nonNullRate;

        /**
         * 样例数据
         */
        private List<String> sampleData;
    }

    /**
     * 获取状态显示名称
     */
    public static String getStatusDisplayName(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "uploaded":
                return "已上传";
            case "archived":
                return "已归档";
            default:
                return status;
        }
    }
}