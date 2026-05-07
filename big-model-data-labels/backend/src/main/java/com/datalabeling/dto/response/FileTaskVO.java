package com.datalabeling.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件任务视图对象
 */
@Data
public class FileTaskVO {

    /**
     * 任务ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 状态
     */
    private String status;

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
     * 错误信息
     */
    private String errorMessage;

    /**
     * 应用的标签列表
     */
    private List<LabelVO> labels;

    /**
     * 文件原始列（按文件顺序）
     */
    private List<String> columns;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 归档时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime archivedAt;
}
