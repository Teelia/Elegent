package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.DataRowVO;
import com.datalabeling.entity.DataRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据行DTO转换器
 */
@Component
public class DataRowMapper {

    /**
     * Entity转VO
     */
    public DataRowVO toVO(DataRow dataRow) {
        if (dataRow == null) {
            return null;
        }

        DataRowVO vo = new DataRowVO();
        vo.setId(dataRow.getId());
        vo.setTaskId(dataRow.getTaskId());
        vo.setRowIndex(dataRow.getRowIndex());
        vo.setOriginalData(dataRow.getOriginalData());
        vo.setLabelResults(dataRow.getLabelResults());
        vo.setAiConfidence(dataRow.getAiConfidence());
        vo.setAiReasoning(dataRow.getAiReasoning());
        vo.setConfidenceThreshold(dataRow.getConfidenceThreshold());
        vo.setNeedsReview(dataRow.getNeedsReview());
        vo.setIsModified(dataRow.getIsModified());
        vo.setProcessingStatus(dataRow.getProcessingStatus());
        vo.setErrorMessage(dataRow.getErrorMessage());
        vo.setCreatedAt(dataRow.getCreatedAt());
        vo.setUpdatedAt(dataRow.getUpdatedAt());

        return vo;
    }

    /**
     * Entity列表转VO列表
     */
    public List<DataRowVO> toVOList(List<DataRow> dataRows) {
        if (dataRows == null) {
            return null;
        }
        return dataRows.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}
