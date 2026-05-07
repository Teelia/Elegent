package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.LabelResultVO;
import com.datalabeling.entity.LabelResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 标签结果Mapper
 */
@Slf4j
@Component
public class LabelResultMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Entity转VO
     */
    public LabelResultVO toVO(LabelResult entity) {
        if (entity == null) {
            return null;
        }

        LabelResultVO vo = LabelResultVO.builder()
                .id(entity.getId())
                .taskId(entity.getAnalysisTaskId())
                .rowId(entity.getDataRowId())
                .labelId(entity.getLabelId())
                .labelType(entity.getLabelType())
                .result(entity.getResult())
                .extractedData(parseExtractedData(entity.getExtractedData()))
                .aiConfidence(entity.getAiConfidence())
                .aiConfidencePercent(LabelResultVO.toPercent(entity.getAiConfidence()))
                .confidenceThreshold(entity.getConfidenceThreshold())
                .confidenceThresholdPercent(LabelResultVO.toPercent(entity.getConfidenceThreshold()))
                .needsReview(entity.getNeedsReview())
                .isModified(entity.getIsModified())
                .aiReason(entity.getAiReasoning())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();

        return vo;
    }

    /**
     * 解析提取数据JSON
     */
    private Map<String, Object> parseExtractedData(String extractedDataJson) {
        if (extractedDataJson == null || extractedDataJson.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(extractedDataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析提取数据失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Entity转VO（带标签名称）
     */
    public LabelResultVO toVO(LabelResult entity, String labelName) {
        LabelResultVO vo = toVO(entity);
        if (vo != null) {
            vo.setLabelName(labelName);
        }
        return vo;
    }
    
    /**
     * Entity转VO（带完整信息）
     */
    public LabelResultVO toVO(LabelResult entity, String labelName,
                              Integer rowIndex, String originalContent,
                              Map<String, Object> originalData) {
        LabelResultVO vo = toVO(entity, labelName);
        if (vo != null) {
            vo.setRowIndex(rowIndex);
            vo.setOriginalContent(originalContent);
            vo.setOriginalData(originalData);
        }
        return vo;
    }
}