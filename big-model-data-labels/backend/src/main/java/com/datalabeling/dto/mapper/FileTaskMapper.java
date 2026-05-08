package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.FileTaskVO;
import com.datalabeling.entity.FileTask;
import org.springframework.stereotype.Component;

/**
 * 文件任务DTO转换器
 */
@Component
public class FileTaskMapper {

    /**
     * Entity转VO
     */
    public FileTaskVO toVO(FileTask task) {
        if (task == null) {
            return null;
        }

        FileTaskVO vo = new FileTaskVO();
        vo.setId(task.getId());
        vo.setUserId(task.getUserId());
        vo.setFilename(task.getFilename());
        vo.setOriginalFilename(task.getOriginalFilename());
        vo.setFileSize(task.getFileSize());
        vo.setStatus(task.getStatus());
        vo.setTotalRows(task.getTotalRows());
        vo.setProcessedRows(task.getProcessedRows());
        vo.setFailedRows(task.getFailedRows());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setColumns(task.getColumns());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setStartedAt(task.getStartedAt());
        vo.setCompletedAt(task.getCompletedAt());
        vo.setArchivedAt(task.getArchivedAt());

        return vo;
    }
}
