package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.DatasetVO;
import com.datalabeling.entity.Dataset;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据集Mapper
 */
@Component
public class DatasetMapper {
    
    /**
     * Entity转VO
     */
    public DatasetVO toVO(Dataset entity) {
        if (entity == null) {
            return null;
        }

        DatasetVO vo = DatasetVO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .originalFilename(entity.getOriginalFilename())
                .totalRows(entity.getTotalRows())
                .status(entity.getStatus())
                .statusDisplay(DatasetVO.getStatusDisplayName(entity.getStatus()))
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
        
        // 转换列信息
        if (entity.getColumns() != null) {
            List<DatasetVO.ColumnInfo> columnInfos = new ArrayList<>();
            for (Map<String, Object> col : entity.getColumns()) {
                DatasetVO.ColumnInfo info = DatasetVO.ColumnInfo.builder()
                        .index(col.get("index") != null ? ((Number) col.get("index")).intValue() : null)
                        .name((String) col.get("name"))
                        .dataType((String) col.get("type"))
                        .nonNullRate(col.get("nonNullRate") != null ? ((Number) col.get("nonNullRate")).doubleValue() : null)
                        .build();
                
                // 处理样例数据
                Object sampleValues = col.get("sampleValues");
                if (sampleValues instanceof List) {
                    List<String> samples = new ArrayList<>();
                    for (Object sample : (List<?>) sampleValues) {
                        samples.add(sample != null ? sample.toString() : null);
                    }
                    info.setSampleData(samples);
                }
                
                columnInfos.add(info);
            }
            vo.setColumns(columnInfos);
        }
        
        return vo;
    }
    
    /**
     * Entity转VO（带额外统计信息）
     */
    public DatasetVO toVO(Dataset entity, Integer taskCount, String latestTaskStatus,
                          Integer latestTaskProgress, Integer datasetLabelCount) {
        DatasetVO vo = toVO(entity);
        if (vo != null) {
            vo.setTaskCount(taskCount);
            vo.setLatestTaskStatus(latestTaskStatus);
            vo.setLatestTaskProgress(latestTaskProgress);
            vo.setDatasetLabelCount(datasetLabelCount);
        }
        return vo;
    }
}