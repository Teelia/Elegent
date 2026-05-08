package com.datalabeling.service;

import com.datalabeling.dto.response.DatabaseExplorerVO;
import com.datalabeling.dto.response.TablePreviewVO;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.repository.SyncConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 外部数据源服务
 * 提供数据库/表浏览、数据预览等功能
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Service
@Slf4j
public class ExternalDataSourceService {

    @Autowired
    private ExternalDataSourceManager dataSourceManager;

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    /**
     * 浏览数据库和表
     *
     * @param syncConfigId 同步配置ID
     * @return 数据库浏览器响应
     */
    public DatabaseExplorerVO exploreDatabases(Integer syncConfigId) {
        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            DatabaseExplorerVO result = new DatabaseExplorerVO();

            switch (config.getDbType().toLowerCase()) {
                case "mysql":
                case "postgresql":
                    result.setDatabases(listDatabases(metaData, config));
                    break;

                case "oracle":
                    // Oracle 使用 Schema 代替 Database
                    result.setDatabases(listOracleSchemas(metaData, config));
                    break;

                case "sqlserver":
                    result.setDatabases(listSqlServerDatabases(metaData, config));
                    break;

                default:
                    throw new IllegalArgumentException("不支持的数据库类型: " + config.getDbType());
            }

            log.info("浏览数据库: syncConfigId={}, 数据库数={}",
                    syncConfigId, result.getDatabases().size());

            return result;

        } catch (SQLException e) {
            log.error("浏览数据库失败: syncConfigId=" + syncConfigId, e);
            throw new RuntimeException("浏览数据库失败: " + e.getMessage());
        }
    }

    /**
     * 列出 MySQL/PostgreSQL 数据库
     */
    private List<DatabaseExplorerVO.DatabaseItem> listDatabases(
            DatabaseMetaData metaData, SyncConfig config) throws SQLException {

        List<DatabaseExplorerVO.DatabaseItem> databases = new ArrayList<>();

        // 如果指定了数据库，只查询该数据库
        String databasePattern = config.getDatabaseName();

        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                String dbName = rs.getString("TABLE_CAT");

                // 过滤系统数据库
                if (isSystemDatabase(dbName, config.getDbType())) {
                    continue;
                }

                if (databasePattern != null && !dbName.equals(databasePattern)) {
                    continue;
                }

                DatabaseExplorerVO.DatabaseItem db = DatabaseExplorerVO.DatabaseItem.builder()
                        .name(dbName)
                        .tables(listTables(metaData, dbName, null, config.getDbType()))
                        .build();
                databases.add(db);
            }
        }

        return databases;
    }

    /**
     * 列出 Oracle Schema（类似数据库的概念）
     */
    private List<DatabaseExplorerVO.DatabaseItem> listOracleSchemas(
            DatabaseMetaData metaData, SyncConfig config) throws SQLException {

        List<DatabaseExplorerVO.DatabaseItem> schemas = new ArrayList<>();

        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                String schemaName = rs.getString("TABLE_SCHEM");

                // 过滤系统 Schema
                if (isOracleSystemSchema(schemaName)) {
                    continue;
                }

                DatabaseExplorerVO.DatabaseItem schema = DatabaseExplorerVO.DatabaseItem.builder()
                        .name(schemaName)
                        .tables(listTables(metaData, null, schemaName, "oracle"))
                        .build();
                schemas.add(schema);
            }
        }

        return schemas;
    }

    /**
     * 列出 SQL Server 数据库
     */
    private List<DatabaseExplorerVO.DatabaseItem> listSqlServerDatabases(
            DatabaseMetaData metaData, SyncConfig config) throws SQLException {

        List<DatabaseExplorerVO.DatabaseItem> databases = new ArrayList<>();

        // SQL Server 需要特殊处理
        String databasePattern = config.getDatabaseName();

        // 尝试从连接中获取当前数据库
        try (Statement stmt = metaData.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sys.databases WHERE database_id > 4")) {

            while (rs.next()) {
                String dbName = rs.getString("name");

                if (databasePattern != null && !dbName.equals(databasePattern)) {
                    continue;
                }

                // 获取该数据库的表
                DatabaseExplorerVO.DatabaseItem db = DatabaseExplorerVO.DatabaseItem.builder()
                        .name(dbName)
                        .tables(listSqlServerTables(metaData, dbName))
                        .build();
                databases.add(db);
            }
        }

        return databases;
    }

    /**
     * 列出表
     */
    private List<DatabaseExplorerVO.TableItem> listTables(
            DatabaseMetaData metaData, String catalog, String schemaPattern, String dbType)
            throws SQLException {

        List<DatabaseExplorerVO.TableItem> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%",
                new String[]{"TABLE", "VIEW"})) {

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");

                // 过滤系统表
                if (isSystemTable(tableName, dbType)) {
                    continue;
                }

                DatabaseExplorerVO.TableItem table = DatabaseExplorerVO.TableItem.builder()
                        .name(tableName)
                        .type(tableType)
                        .build();
                tables.add(table);
            }
        }

        return tables;
    }

    /**
     * 列出 SQL Server 表
     */
    private List<DatabaseExplorerVO.TableItem> listSqlServerTables(
            DatabaseMetaData metaData, String databaseName) throws SQLException {

        List<DatabaseExplorerVO.TableItem> tables = new ArrayList<>();

        // 切换到目标数据库
        try (Statement stmt = metaData.getConnection().createStatement()) {
            stmt.execute("USE [" + databaseName + "]");
        }

        try (ResultSet rs = metaData.getTables(databaseName, null, "%",
                new String[]{"TABLE", "VIEW"})) {

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String tableType = rs.getString("TABLE_TYPE");

                if (isSystemTable(tableName, "sqlserver")) {
                    continue;
                }

                DatabaseExplorerVO.TableItem table = DatabaseExplorerVO.TableItem.builder()
                        .name(tableName)
                        .type(tableType)
                        .build();
                tables.add(table);
            }
        }

        return tables;
    }

    /**
     * 预览表数据（前20行）
     *
     * @param syncConfigId 同步配置ID
     * @param tableName    表名
     * @param whereClause  WHERE 条件（可选）
     * @return 表预览响应
     */
    public TablePreviewVO previewTableData(Integer syncConfigId, String tableName,
                                           String whereClause) {

        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

        String sql = buildSelectSql(config, tableName, whereClause, 20);

        log.info("预览表数据: syncConfigId={}, table={}, sql={}",
                syncConfigId, tableName, sql);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            TablePreviewVO preview = TablePreviewVO.builder()
                    .tableName(tableName)
                    .sql(sql)
                    .build();

            // 提取列名
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            preview.setColumns(columns);

            // 提取数据行
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }
            preview.setRows(rows);

            // 获取总行数（估算）
            preview.setTotalRows(estimateRowCount(conn, tableName));

            return preview;

        } catch (SQLException e) {
            log.error("预览表数据失败: syncConfigId=" + syncConfigId + ", table=" + tableName, e);
            throw new RuntimeException("预览失败: " + e.getMessage());
        }
    }

    /**
     * 构建 SELECT SQL 语句
     */
    private String buildSelectSql(SyncConfig config, String tableName,
                                  String whereClause, int limit) {

        String qualifiedTableName = buildQualifiedTableName(config, tableName);
        String sql = "SELECT * FROM " + qualifiedTableName;

        if (StringUtils.isNotBlank(whereClause)) {
            sql += " WHERE " + whereClause;
        }

        // 添加 LIMIT 子句
        switch (config.getDbType().toLowerCase()) {
            case "mysql":
            case "postgresql":
                if (limit > 0) {
                    sql += " LIMIT " + limit;
                }
                break;

            case "oracle":
                if (limit > 0) {
                    sql = "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
                }
                break;

            case "sqlserver":
                if (limit > 0) {
                    sql = "SELECT TOP " + limit + " * FROM " + qualifiedTableName;
                    if (StringUtils.isNotBlank(whereClause)) {
                        sql += " WHERE " + whereClause;
                    }
                }
                break;
        }

        return sql;
    }

    /**
     * 构建限定表名（包含 schema/database 前缀）
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
                // Oracle 表名通常不需要 database 前缀，使用大写
                return "\"" + tableName.toUpperCase() + "\"";

            case "sqlserver":
                return "[" + config.getDatabaseName() + "].[dbo].[" + tableName + "]";

            default:
                return tableName;
        }
    }

    /**
     * 估算表的行数
     */
    private Long estimateRowCount(Connection conn, String tableName) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.warn("获取表行数失败: {}", tableName, e);
        }
        return null;
    }

    /**
     * 判断是否为系统数据库
     */
    private boolean isSystemDatabase(String dbName, String dbType) {
        if ("mysql".equalsIgnoreCase(dbType)) {
            return "information_schema".equals(dbName) ||
                    "mysql".equals(dbName) ||
                    "performance_schema".equals(dbName) ||
                    "sys".equals(dbName);
        } else if ("postgresql".equalsIgnoreCase(dbType)) {
            return "postgres".equals(dbName) ||
                    "template0".equals(dbName) ||
                    "template1".equals(dbName);
        }
        return false;
    }

    /**
     * 判断是否为 Oracle 系统 Schema
     */
    private boolean isOracleSystemSchema(String schemaName) {
        return schemaName != null && (
                schemaName.startsWith("SYS") ||
                "DBSNMP".equals(schemaName) ||
                "SYSTEM".equals(schemaName) ||
                "OUTLN".equals(schemaName) ||
                schemaName.startsWith("EXFSYS") ||
                "WMSYS".equals(schemaName) ||
                "CTXSYS".equals(schemaName) ||
                "XDB".equals(schemaName) ||
                "ANONYMOUS".equals(schemaName) ||
                "ORDDATA".equals(schemaName) ||
                "ORDSYS".equals(schemaName) ||
                "MDSYS".equals(schemaName) ||
                "OLAPSYS".equals(schemaName)
        );
    }

    /**
     * 判断是否为系统表
     */
    private boolean isSystemTable(String tableName, String dbType) {
        if (tableName == null) {
            return false;
        }
        return tableName.startsWith("sys_") ||
                tableName.startsWith("pg_") ||
                tableName.startsWith("information_schema");
    }
}
