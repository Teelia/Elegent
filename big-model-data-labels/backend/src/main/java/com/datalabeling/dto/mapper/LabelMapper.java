package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.entity.Label;
import org.springframework.stereotype.Component;

/**
 * 标签DTO转换器
 */
@Component
public class LabelMapper {

    /**
     * Entity转VO
     */
    public LabelVO toVO(Label label) {
        if (label == null) {
            return null;
        }

        LabelVO vo = new LabelVO();
        vo.setId(label.getId());
        vo.setUserId(label.getUserId());
        vo.setName(label.getName());
        vo.setVersion(label.getVersion());
        vo.setScope(label.getScope());
        vo.setType(label.getType());
        vo.setTaskId(label.getTaskId());
        vo.setDescription(label.getDescription());
        vo.setFocusColumns(label.getFocusColumns());
        vo.setExtractFields(label.getExtractFields());
        vo.setExtractorConfig(label.getExtractorConfig());
        vo.setPreprocessingMode(label.getPreprocessingMode());
        vo.setPreprocessorConfig(label.getPreprocessorConfig());
        vo.setIncludePreprocessorInPrompt(label.getIncludePreprocessorInPrompt());
        vo.setEnableEnhancement(label.getEnableEnhancement());
        vo.setEnhancementConfig(label.getEnhancementConfig());
        vo.setDatasetId(label.getDatasetId());
        vo.setIsActive(label.getIsActive());
        vo.setBuiltinLevel(label.getBuiltinLevel());
        vo.setBuiltinCategory(label.getBuiltinCategory());
        vo.setCreatedAt(label.getCreatedAt());
        vo.setUpdatedAt(label.getUpdatedAt());

        return vo;
    }
}
