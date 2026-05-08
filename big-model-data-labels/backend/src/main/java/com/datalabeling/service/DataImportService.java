package com.datalabeling.service;

import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.Dataset;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.DatasetRepository;
import com.datalabeling.repository.SyncConfigRepository;
import com.datalabeling.service.ExternalDataSourceManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据导入服务
 * 提供从外部数据源导入数据到数据集的功能
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Service
@Slf4j
public class DataImportService {

    @Autowired
    private ExternalDataSourceManager dataSourceManager;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DataRowRepository dataRowRepository;

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 批量插入的批次大小（优化后：5000 行/批）
     */
    private static final int BATCH_SIZE = 5000;

    /**
     * 进度更新频率（每 N 批更新一次）
     */
    private static final int PROGRESS_UPDATE_BATCHES = 5; // 每 5 批（25000 行）更新一次进度

    /**
     * 启动数据导入任务（异步）
     *
     * @param syncConfigId 同步配置ID
     * @param datasetId    数据集ID
     * @param tableName    表名
     * @param whereClause  WHERE 条件（可选）
     */
    @Async("importTaskExecutor")
    public void importData(Integer syncConfigId, Integer datasetId, String tableName, String whereClause) {

        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + datasetId));

        // 更新状态：导入中
        config.setImportStatus("importing");
        syncConfigRepository.save(config);

        dataset.setStatus(Dataset.Status.IMPORTING);
        datasetRepository.save(dataset);

        log.info("开始导入数据: syncConfigId={}, datasetId={}, table={}",
                syncConfigId, datasetId, tableName);

        DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

        int totalImported = 0;
        int batchCount = 0;
        long startTime = System.currentTimeMillis();

        try (Connection conn = dataSource.getConnection()) {
            // 构建查询 SQL
            String sql = buildSelectSql(config, tableName, whereClause, -1);

            log.info("执行查询SQL: {}", sql);

            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {

                // 设置流式读取（对于大表）
                stmt.setFetchSize(BATCH_SIZE);

                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    // 提取列信息
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnLabel(i));
                    }

                    log.info("表有 {} 列: {}", columns.size(), columns);

                    // 构建 Dataset 的 columns JSON
                    List<Map<String, Object>> columnInfo = buildColumnInfo(metaData, columns);

                    // 更新 Dataset 列信息（仅在开始时更新一次）
                    dataset.setColumns(columnInfo);
                    dataset.setTotalRows(0);
                    datasetRepository.save(dataset);

                    // 批量读取并插入
                    List<DataRow> batch = new ArrayList<>(BATCH_SIZE);

                    while (rs.next()) {
                        Map<String, Object> rowData = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            // 处理 CLOB 等大对象
                            if (value instanceof Clob) {
                                value = clobToString((Clob) value);
                            }
                            rowData.put(columns.get(i - 1), value);
                        }

                        DataRow dataRow = DataRow.builder()
                                .taskId(datasetId)
                                .rowIndex(++totalImported)
                                .originalData(rowData)
                                .processingStatus("success")
                                .build();
                        batch.add(dataRow);

                        // 达到批次大小时插入
                        if (batch.size() >= BATCH_SIZE) {
                            saveBatch(batch);
                            batchCount++;
                            batch.clear();

                            // 仅在特定批次更新进度，减少数据库写操作
                            if (batchCount % PROGRESS_UPDATE_BATCHES == 0) {
                                dataset.setTotalRows(totalImported);
                                datasetRepository.save(dataset);
                                log.info("导入进度: datasetId={}, 已导入 {} 行", datasetId, totalImported);
                            }
                        }
                    }

                    // 插入剩余行
                    if (!batch.isEmpty()) {
                        saveBatch(batch);
                        batch.clear();
                    }

                    // 更新最终状态
                    dataset.setTotalRows(totalImported);
                    dataset.setStatus(Dataset.Status.UPLOADED);
                    datasetRepository.save(dataset);

                    config.setImportStatus("completed");
                    config.setLastImportTime(LocalDateTime.now());
                    syncConfigRepository.save(config);

                    long elapsed = System.currentTimeMillis() - startTime;
                    log.info("数据导入完成: syncConfigId={}, datasetId={}, 总行数={}, 耗时={}ms",
                            syncConfigId, datasetId, totalImported, elapsed);

                }
            }

        } catch (Exception e) {
            log.error("数据导入失败: syncConfigId=" + syncConfigId + ", datasetId=" + datasetId, e);
            config.setImportStatus("failed");
            syncConfigRepository.save(config);

            dataset.setStatus(Dataset.Status.FAILED);
            datasetRepository.save(dataset);

            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    /**
     * 构建列信息
     */
    private List<Map<String, Object>> buildColumnInfo(ResultSetMetaData metaData,
                                                      List<String> columns) throws SQLException {
        List<Map<String, Object>> columnInfo = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> col = new HashMap<>();
            col.put("index", i);
            col.put("name", columns.get(i));
            col.put("dataType", metaData.getColumnTypeName(i + 1));
            columnInfo.add(col);
        }
        return columnInfo;
    }

    /**
     * 构建 SELECT SQL 语句
     */
    private String buildSelectSql(SyncConfig config, String tableName,
                                  String whereClause, int limit) {

        // 验证表名不为空
        if (StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("表名不能为空");
        }

        String qualifiedTableName = buildQualifiedTableName(config, tableName);
        String sql = "SELECT * FROM " + qualifiedTableName;

        if (StringUtils.isNotBlank(whereClause)) {
            sql += " WHERE " + whereClause;
        }

        // 注意：这里不添加 LIMIT，因为需要全量导入
        // 如果需要限制导入行数，应该通过 whereClause 参数控制

        return sql;
    }

    /**
     * 构建限定表名
     */
    private String buildQualifiedTableName(SyncConfig config, String tableName) {
        // 如果表名已经包含 schema 前缀，直接使用
        if (tableName.contains(".")) {
            return tableName;
        }

        String dbType = config.getDbType().toLowerCase();

        switch (dbType) {
            case "mysql":
                return "`" + config.getDatabaseName() + "`.`" + tableName + "`";

            case "postgresql":
                return "\"" + config.getDatabaseName() + "\".\"" + tableName + "\"";

            case "oracle":
                return "\"" + tableName.toUpperCase() + "\"";

            case "sqlserver":
                return "[" + config.getDatabaseName() + "].[dbo].[" + tableName + "]";

            default:
                return tableName;
        }
    }

    /**
     * 批量保存数据行
     */
    private void saveBatch(List<DataRow> batch) {
        dataRowRepository.saveAll(batch);
        dataRowRepository.flush();
        // 清理一级缓存，避免内存溢出
        entityManager.clear();
    }

    /**
     * CLOB 转字符串
     */
    private String clobToString(Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try (java.io.Reader reader = clob.getCharacterStream()) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        } catch (java.io.IOException e) {
            log.error("读取 CLOB 失败", e);
            return clob.toString();
        }
        return sb.toString();
    }

    /**
     * 创建数据集并开始导入
     *
     * @param syncConfigId 同步配置ID
     * @param tableName    表名
     * @param datasetName  数据集名称
     * @param importQuery  导入查询条件
     * @param description  描述
     * @return 创建的数据集ID
     */
    public Integer createDatasetAndImport(Integer syncConfigId, String tableName,
                                          String datasetName, String importQuery, String description) {

        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        // 创建数据集
        Dataset dataset = Dataset.builder()
                .userId(config.getUserId())
                .name(datasetName)
                .description(description)
                .sourceType(Dataset.SourceType.DATABASE)
                .externalSourceId(syncConfigId)
                .importQuery(importQuery)
                .status(Dataset.Status.IMPORTING) // 初始状态为导入中
                .totalRows(0)
                .build();

        Dataset savedDataset = datasetRepository.save(dataset);

        // 异步导入数据
        importData(syncConfigId, savedDataset.getId(), tableName, importQuery);

        return savedDataset.getId();
    }

    /**
     * 增量更新数据集（异步）
     *
     * @param datasetId 数据集ID
     * @param mode      更新模式：append=追加, replace=替换
     */
    @Async("importTaskExecutor")
    public void incrementUpdate(Integer datasetId, String mode) {

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在: " + datasetId));

        // 验证数据集来源
        if (!Dataset.SourceType.DATABASE.equals(dataset.getSourceType())) {
            throw new IllegalArgumentException("只有数据库导入的数据集才能进行增量更新");
        }

        Integer syncConfigId = dataset.getExternalSourceId();
        if (syncConfigId == null) {
            throw new IllegalArgumentException("数据集没有关联的外部数据源");
        }

        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        // 更新状态
        config.setImportStatus("importing");
        syncConfigRepository.save(config);

        log.info("开始增量更新: datasetId={}, mode={}, table={}",
                datasetId, mode, config.getTableName());

        int batchCount = 0;
        long startTime = System.currentTimeMillis();

        // 构建增量查询条件
        String incrementalWhereClause = buildIncrementalWhereClause(dataset, config);

        log.info("增量更新条件: {}", incrementalWhereClause);

        try {
            // 如果是替换模式，先清空现有数据
            if ("replace".equals(mode)) {
                log.info("替换模式：清空现有数据 datasetId={}", datasetId);
                dataRowRepository.deleteByTaskId(datasetId);
                dataset.setTotalRows(0);
                datasetRepository.save(dataset);
            }

            // 执行导入
            DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

            try (Connection conn = dataSource.getConnection()) {
                // 构建查询 SQL（结合原有查询条件和增量条件）
                String sql = buildIncrementalSql(config, config.getTableName(),
                        dataset.getImportQuery(), incrementalWhereClause);

                log.info("执行增量查询SQL: {}", sql);

                try (Statement stmt = conn.createStatement(
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY)) {

                    // 设置流式读取
                    stmt.setFetchSize(BATCH_SIZE);

                    try (ResultSet rs = stmt.executeQuery(sql)) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        // 提取列信息
                        List<String> columns = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            columns.add(metaData.getColumnLabel(i));
                        }

                        // 获取当前最大行索引（用于追加模式）
                        int startRowIndex = dataset.getTotalRows();
                        if ("append".equals(mode)) {
                            startRowIndex = dataRowRepository.findMaxRowIndexByTaskId(datasetId);
                            if (startRowIndex == 0) {
                                startRowIndex = dataset.getTotalRows();
                            }
                        }

                        // 批量读取并插入
                        List<DataRow> batch = new ArrayList<>(BATCH_SIZE);

                        while (rs.next()) {
                            Map<String, Object> rowData = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                Object value = rs.getObject(i);
                                // 处理 CLOB 等大对象
                                if (value instanceof Clob) {
                                    value = clobToString((Clob) value);
                                }
                                rowData.put(columns.get(i - 1), value);
                            }

                            DataRow dataRow = DataRow.builder()
                                    .taskId(datasetId)
                                    .rowIndex(++startRowIndex)
                                    .originalData(rowData)
                                    .processingStatus("success")
                                    .build();
                            batch.add(dataRow);

                            // 达到批次大小时插入
                            if (batch.size() >= BATCH_SIZE) {
                                saveBatch(batch);
                                batchCount++;
                                batch.clear();

                                // 更新进度
                                dataset.setTotalRows(startRowIndex);
                                datasetRepository.save(dataset);

                                log.info("增量更新进度: datasetId={}, 已导入 {} 行", datasetId, startRowIndex);
                            }
                        }

                        // 插入剩余行
                        if (!batch.isEmpty()) {
                            saveBatch(batch);
                            batch.clear();
                        }

                        // 更新最终状态
                        dataset.setTotalRows(startRowIndex);
                        dataset.setStatus(Dataset.Status.UPLOADED);
                        dataset.setLastImportTime(LocalDateTime.now());
                        datasetRepository.save(dataset);

                        config.setImportStatus("completed");
                        config.setLastImportTime(LocalDateTime.now());
                        syncConfigRepository.save(config);

                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("增量更新完成: datasetId={}, mode={}, 总行数={}, 耗时={}ms",
                                datasetId, mode, startRowIndex, elapsed);

                    }
                }

            }

        } catch (Exception e) {
            log.error("增量更新失败: datasetId=" + datasetId + ", mode=" + mode, e);
            config.setImportStatus("failed");
            syncConfigRepository.save(config);
            throw new RuntimeException("增量更新失败: " + e.getMessage());
        }
    }

    /**
     * 构建增量查询条件
     */
    private String buildIncrementalWhereClause(Dataset dataset, SyncConfig config) {
        LocalDateTime lastImportTime = dataset.getLastImportTime();

        // 如果数据集没有导入记录，返回空条件（全量导入）
        if (lastImportTime == null) {
            return null;
        }

        // 如果配置了时间戳列，使用该列进行过滤
        if (StringUtils.isNotBlank(config.getTimestampColumn())) {
            String timestampColumn = config.getTimestampColumn();
            String dbType = config.getDbType().toLowerCase();

            // 根据数据库类型构建时间戳比较条件
            String timestampCondition;
            switch (dbType) {
                case "mysql":
                    timestampCondition = String.format("`%s` > '%s'",
                            timestampColumn, lastImportTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                case "postgresql":
                    timestampCondition = String.format("\"%s\" > '%s'",
                            timestampColumn, lastImportTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                case "oracle":
                    timestampCondition = String.format("\"%s\" > TO_DATE('%s', 'YYYY-MM-DD HH24:MI:SS')",
                            timestampColumn.toUpperCase(), lastImportTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                case "sqlserver":
                    timestampCondition = String.format("[%s] > '%s'",
                            timestampColumn, lastImportTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                default:
                    timestampCondition = String.format("%s > '%s'", timestampColumn,
                            lastImportTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }

            return timestampCondition;
        }

        // 如果没有配置时间戳列，无法进行增量更新
        log.warn("数据集 {} 没有配置时间戳列，将执行全量更新", dataset.getId());
        return null;
    }

    /**
     * 构建增量查询 SQL
     */
    private String buildIncrementalSql(SyncConfig config, String tableName,
                                       String originalQuery, String incrementalWhereClause) {
        // 验证表名不为空
        if (StringUtils.isBlank(tableName)) {
            throw new IllegalArgumentException("增量更新失败：表名不能为空，请检查数据源配置");
        }

        String qualifiedTableName = buildQualifiedTableName(config, tableName);
        String sql = "SELECT * FROM " + qualifiedTableName;

        List<String> conditions = new ArrayList<>();

        // 添加原有查询条件
        if (StringUtils.isNotBlank(originalQuery)) {
            conditions.add("(" + originalQuery + ")");
        }

        // 添加增量条件
        if (StringUtils.isNotBlank(incrementalWhereClause)) {
            conditions.add("(" + incrementalWhereClause + ")");
        }

        // 组合所有条件
        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }

        return sql;
    }
}
