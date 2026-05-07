package com.datalabeling.dto.mapper;

import com.datalabeling.dto.response.SyncConfigVO;
import com.datalabeling.entity.SyncConfig;
import org.springframework.stereotype.Component;

/**
 * 同步配置DTO转换器
 */
@Component
public class SyncConfigMapper {

    public SyncConfigVO toVO(SyncConfig config) {
        if (config == null) {
            return null;
        }

        SyncConfigVO vo = new SyncConfigVO();
        vo.setId(config.getId());
        vo.setUserId(config.getUserId());
        vo.setName(config.getName());
        vo.setDbType(config.getDbType());
        vo.setHost(config.getHost());
        vo.setPort(config.getPort());
        vo.setDatabaseName(config.getDatabaseName());
        vo.setUsername(config.getUsername());
        vo.setTableName(config.getTableName());
        vo.setFieldMappings(config.getFieldMappings());
        vo.setIsActive(config.getIsActive());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}

