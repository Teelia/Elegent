package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.response.DatabaseExplorerVO;
import com.datalabeling.dto.response.TablePreviewVO;
import com.datalabeling.entity.Dataset;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.repository.DatasetRepository;
import com.datalabeling.repository.SyncConfigRepository;
import com.datalabeling.service.DataImportService;
import com.datalabeling.service.ExternalDataSourceManager;
import com.datalabeling.service.ExternalDataSourceService;
import com.datalabeling.util.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部数据源管理 API 控制器
 * 提供数据源 CRUD、连接测试、数据库浏览、数据预览、数据导入等功能
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Slf4j
@RestController
@RequestMapping("/data-sources")
public class ExternalDataSourceController {

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private ExternalDataSourceService externalDataSourceService;

    @Autowired
    private ExternalDataSourceManager externalDataSourceManager;

    @Autowired
    private DataImportService dataImportService;

    @Autowired
    private SecurityUtil securityUtil;

    /**
     * 创建数据源配置
     */
    @PostMapping
    public ApiResponse<SyncConfig> createDataSource(@Valid @RequestBody CreateDataSourceRequest request) {
        Integer userId = securityUtil.getCurrentUserId();

        SyncConfig config = SyncConfig.builder()
                .userId(userId)
                .name(request.getName())
                .direction(request.getDirection())
                .dbType(request.getDbType())
                .host(request.getHost())
                .port(request.getPort())
                .databaseName(request.getDatabaseName())
                .oracleSid(request.getOracleSid())
                .oracleServiceName(request.getOracleServiceName())
                .connectionMode(request.getConnectionMode())
                .username(request.getUsername())
                .passwordEncrypted(request.getPassword()) // TODO: 加密
                .tableName(request.getTableName())
                .importQuery(request.getImportQuery())
                .timestampColumn(request.getTimestampColumn())
                .isActive(true)
                .build();

        SyncConfig saved = syncConfigRepository.save(config);
        return ApiResponse.success(saved);
    }

    /**
     * 更新数据源配置
     */
    @PutMapping("/{id}")
    public ApiResponse<SyncConfig> updateDataSource(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateDataSourceRequest request) {

        Integer userId = securityUtil.getCurrentUserId();

        SyncConfig config = syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        // 更新字段
        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getDirection() != null) {
            config.setDirection(request.getDirection());
        }
        if (request.getHost() != null) {
            config.setHost(request.getHost());
        }
        if (request.getPort() != null) {
            config.setPort(request.getPort());
        }
        if (request.getDatabaseName() != null) {
            config.setDatabaseName(request.getDatabaseName());
        }
        if (request.getOracleSid() != null) {
            config.setOracleSid(request.getOracleSid());
        }
        if (request.getOracleServiceName() != null) {
            config.setOracleServiceName(request.getOracleServiceName());
        }
        if (request.getConnectionMode() != null) {
            config.setConnectionMode(request.getConnectionMode());
        }
        if (request.getUsername() != null) {
            config.setUsername(request.getUsername());
        }
        if (request.getPassword() != null) {
            config.setPasswordEncrypted(request.getPassword()); // TODO: 加密
        }
        if (request.getTableName() != null) {
            config.setTableName(request.getTableName());
        }
        if (request.getImportQuery() != null) {
            config.setImportQuery(request.getImportQuery());
        }
        if (request.getTimestampColumn() != null) {
            config.setTimestampColumn(request.getTimestampColumn());
        }

        SyncConfig saved = syncConfigRepository.save(config);
        return ApiResponse.success(saved);
    }

    /**
     * 删除数据源配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDataSource(@PathVariable Integer id) {
        Integer userId = securityUtil.getCurrentUserId();

        SyncConfig config = syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        // 清理数据源缓存
        externalDataSourceManager.evictDataSource(id);

        syncConfigRepository.delete(config);

        return ApiResponse.success();
    }

    /**
     * 获取数据源列表（仅导入类型）
     */
    @GetMapping
    public ApiResponse<java.util.List<SyncConfig>> listDataSources() {
        Integer userId = securityUtil.getCurrentUserId();

        java.util.List<SyncConfig> configs = syncConfigRepository
                .findByUserIdAndDirection(userId, "import");

        return ApiResponse.success(configs);
    }

    /**
     * 获取数据源详情
     */
    @GetMapping("/{id}")
    public ApiResponse<SyncConfig> getDataSource(@PathVariable Integer id) {
        Integer userId = securityUtil.getCurrentUserId();

        SyncConfig config = syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        // 隐藏密码
        SyncConfig masked = maskPassword(config);

        return ApiResponse.success(masked);
    }

    /**
     * 测试连接
     */
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> testConnection(@PathVariable Integer id) {
        Integer userId = securityUtil.getCurrentUserId();

        syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        boolean success = externalDataSourceManager.testConnection(id);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "连接成功" : "连接失败");

        return ApiResponse.success(result);
    }

    /**
     * 浏览数据库和表
     */
    @GetMapping("/{id}/explore")
    public ApiResponse<DatabaseExplorerVO> exploreDatabases(@PathVariable Integer id) {
        Integer userId = securityUtil.getCurrentUserId();

        syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        DatabaseExplorerVO result = externalDataSourceService.exploreDatabases(id);

        return ApiResponse.success(result);
    }

    /**
     * 预览表数据
     */
    @GetMapping("/{id}/preview")
    public ApiResponse<TablePreviewVO> previewTableData(
            @PathVariable Integer id,
            @RequestParam String tableName,
            @RequestParam(required = false) String whereClause) {

        Integer userId = securityUtil.getCurrentUserId();

        syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        TablePreviewVO result = externalDataSourceService.previewTableData(id, tableName, whereClause);

        return ApiResponse.success(result);
    }

    /**
     * 创建导入任务
     */
    @PostMapping("/{id}/import")
    public ApiResponse<Map<String, Object>> createImportTask(
            @PathVariable Integer id,
            @Valid @RequestBody CreateImportTaskRequest request) {

        Integer userId = securityUtil.getCurrentUserId();

        SyncConfig config = syncConfigRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        // 创建数据集并开始导入
        Integer datasetId = dataImportService.createDatasetAndImport(
                id,
                request.getTableName(),
                request.getDatasetName(),
                request.getImportQuery(),
                request.getDescription()
        );

        Map<String, Object> result = new HashMap<>();
        result.put("datasetId", datasetId);
        result.put("status", "importing");
        result.put("message", "导入任务已创建，正在后台执行");

        return ApiResponse.success(result);
    }

    /**
     * 增量更新数据集
     */
    @PostMapping("/datasets/{id}/increment-update")
    public ApiResponse<Map<String, Object>> incrementUpdateDataset(
            @PathVariable Integer id,
            @Valid @RequestBody IncrementUpdateRequest request) {

        Integer userId = securityUtil.getCurrentUserId();

        Dataset dataset = datasetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));

        // 验证数据集来源
        if (!Dataset.SourceType.DATABASE.equals(dataset.getSourceType())) {
            throw new IllegalArgumentException("只有数据库导入的数据集才能进行增量更新");
        }

        // 异步执行增量更新
        dataImportService.incrementUpdate(id, request.getMode());

        Map<String, Object> result = new HashMap<>();
        result.put("datasetId", id);
        result.put("status", "updating");
        result.put("mode", request.getMode());
        result.put("message", "增量更新任务已创建，正在后台执行");

        return ApiResponse.success(result);
    }

    /**
     * 隐藏密码
     */
    private SyncConfig maskPassword(SyncConfig config) {
        SyncConfig masked = new SyncConfig();
        masked.setId(config.getId());
        masked.setUserId(config.getUserId());
        masked.setName(config.getName());
        masked.setDirection(config.getDirection());
        masked.setDbType(config.getDbType());
        masked.setHost(config.getHost());
        masked.setPort(config.getPort());
        masked.setDatabaseName(config.getDatabaseName());
        masked.setOracleSid(config.getOracleSid());
        masked.setOracleServiceName(config.getOracleServiceName());
        masked.setConnectionMode(config.getConnectionMode());
        masked.setUsername(config.getUsername());
        masked.setPasswordEncrypted("******");
        masked.setTableName(config.getTableName());
        masked.setImportQuery(config.getImportQuery());
        masked.setTimestampColumn(config.getTimestampColumn());
        masked.setIsActive(config.getIsActive());
        masked.setConnectionTestStatus(config.getConnectionTestStatus());
        masked.setConnectionTestTime(config.getConnectionTestTime());
        masked.setImportStatus(config.getImportStatus());
        masked.setLastImportTime(config.getLastImportTime());
        masked.setCreatedAt(config.getCreatedAt());
        masked.setUpdatedAt(config.getUpdatedAt());
        return masked;
    }

    // ========== 请求 DTO ==========

    public static class CreateDataSourceRequest {
        private String name;
        private String direction = "import";
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String oracleSid;
        private String oracleServiceName;
        private String connectionMode = "standard";
        private String username;
        private String password;
        private String tableName;
        private String importQuery;
        private String timestampColumn;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getDbType() { return dbType; }
        public void setDbType(String dbType) { this.dbType = dbType; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getOracleSid() { return oracleSid; }
        public void setOracleSid(String oracleSid) { this.oracleSid = oracleSid; }
        public String getOracleServiceName() { return oracleServiceName; }
        public void setOracleServiceName(String oracleServiceName) { this.oracleServiceName = oracleServiceName; }
        public String getConnectionMode() { return connectionMode; }
        public void setConnectionMode(String connectionMode) { this.connectionMode = connectionMode; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getImportQuery() { return importQuery; }
        public void setImportQuery(String importQuery) { this.importQuery = importQuery; }
        public String getTimestampColumn() { return timestampColumn; }
        public void setTimestampColumn(String timestampColumn) { this.timestampColumn = timestampColumn; }
    }

    public static class UpdateDataSourceRequest {
        private String name;
        private String direction;
        private String host;
        private Integer port;
        private String databaseName;
        private String oracleSid;
        private String oracleServiceName;
        private String connectionMode;
        private String username;
        private String password;
        private String tableName;
        private String importQuery;
        private String timestampColumn;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public String getOracleSid() { return oracleSid; }
        public void setOracleSid(String oracleSid) { this.oracleSid = oracleSid; }
        public String getOracleServiceName() { return oracleServiceName; }
        public void setOracleServiceName(String oracleServiceName) { this.oracleServiceName = oracleServiceName; }
        public String getConnectionMode() { return connectionMode; }
        public void setConnectionMode(String connectionMode) { this.connectionMode = connectionMode; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getImportQuery() { return importQuery; }
        public void setImportQuery(String importQuery) { this.importQuery = importQuery; }
        public String getTimestampColumn() { return timestampColumn; }
        public void setTimestampColumn(String timestampColumn) { this.timestampColumn = timestampColumn; }
    }

    public static class CreateImportTaskRequest {
        private String tableName;
        private String datasetName;
        private String importQuery;
        private String description;

        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getDatasetName() { return datasetName; }
        public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
        public String getImportQuery() { return importQuery; }
        public void setImportQuery(String importQuery) { this.importQuery = importQuery; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class IncrementUpdateRequest {
        /**
         * 更新模式：append=追加, replace=替换
         */
        private String mode = "append";

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }
}
