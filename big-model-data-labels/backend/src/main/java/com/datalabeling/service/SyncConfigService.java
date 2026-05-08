package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.mapper.SyncConfigMapper;
import com.datalabeling.dto.request.CreateSyncConfigRequest;
import com.datalabeling.dto.request.UpdateSyncConfigRequest;
import com.datalabeling.dto.response.SyncConfigVO;
import com.datalabeling.dto.response.TableSchemaVO;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.SyncConfigRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步配置服务
 */
@Service
@RequiredArgsConstructor
public class SyncConfigService {

    private final SyncConfigRepository syncConfigRepository;
    private final SyncConfigMapper syncConfigMapper;
    private final SecurityUtil securityUtil;
    private final SyncCryptoService syncCryptoService;
    private final ExternalDbService externalDbService;
    private final AuditService auditService;

    public List<SyncConfigVO> list(Integer queryUserId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean isAdmin = securityUtil.isAdmin();
        Integer userId = isAdmin ? queryUserId : currentUserId;

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditService.recordAdminRead(queryUserId, "admin_list_sync_configs", "sync_config", null, auditDetails, httpRequest);

        List<SyncConfig> configs;
        if (userId != null) {
            configs = syncConfigRepository.findByUserId(userId);
        } else {
            configs = syncConfigRepository.findAll();
        }

        List<SyncConfigVO> result = new ArrayList<>();
        for (SyncConfig c : configs) {
            result.add(syncConfigMapper.toVO(c));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncConfigVO create(CreateSyncConfigRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Map<String, Object> emptyMappings = new HashMap<>();
        SyncConfig config = SyncConfig.builder()
            .userId(userId)
            .name(request.getName())
            .dbType(request.getDbType())
            .host(request.getHost())
            .port(request.getPort())
            .databaseName(request.getDatabaseName())
            .username(request.getUsername())
            .passwordEncrypted(syncCryptoService.encrypt(request.getPassword()))
            .tableName(request.getTableName())
            .fieldMappings(emptyMappings)
            .isActive(true)
            .build();

        config = syncConfigRepository.save(config);
        return syncConfigMapper.toVO(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public SyncConfigVO update(Integer id, UpdateSyncConfigRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SyncConfig config = syncConfigRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.SYNC_CONFIG_NOT_FOUND));
        if (!securityUtil.hasPermission(config.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        config.setName(request.getName());
        config.setDbType(request.getDbType());
        config.setHost(request.getHost());
        config.setPort(request.getPort());
        config.setDatabaseName(request.getDatabaseName());
        config.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            config.setPasswordEncrypted(syncCryptoService.encrypt(request.getPassword()));
        }
        config.setTableName(request.getTableName());

        config = syncConfigRepository.save(config);
        return syncConfigMapper.toVO(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SyncConfig config = syncConfigRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.SYNC_CONFIG_NOT_FOUND));
        if (!securityUtil.hasPermission(config.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        syncConfigRepository.deleteById(id);
    }

    public TableSchemaVO getTableSchema(Integer id) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SyncConfig config = syncConfigRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.SYNC_CONFIG_NOT_FOUND));
        if (!securityUtil.hasPermission(config.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return TableSchemaVO.builder().columns(externalDbService.getTableSchema(config)).build();
    }
}

