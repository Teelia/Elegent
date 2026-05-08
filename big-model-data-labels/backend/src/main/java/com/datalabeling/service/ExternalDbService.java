package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.dto.response.TableSchemaVO;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 外部数据库访问（表结构读取、连接创建）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalDbService {

    private final SyncCryptoService syncCryptoService;

    public Connection openConnection(SyncConfig config) {
        try {
            String url = buildJdbcUrl(config);
            String password = syncCryptoService.decrypt(config.getPasswordEncrypted());
            return DriverManager.getConnection(url, config.getUsername(), password);
        } catch (Exception e) {
            log.error("外部数据库连接失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DB_CONNECTION_FAILED, "外部数据库连接失败: " + e.getMessage());
        }
    }

    public List<TableSchemaVO.Column> getTableSchema(SyncConfig config) {
        try (Connection conn = openConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();

            QualifiedTable table = QualifiedTable.parse(config.getTableName());
            DbType type = DbType.from(config.getDbType());

            String catalog = null;
            String schema = table.schema;
            if (type == DbType.MYSQL) {
                catalog = config.getDatabaseName();
                schema = schema != null ? schema : null;
            } else if (type == DbType.POSTGRESQL) {
                schema = schema != null ? schema : "public";
            } else if (type == DbType.SQLSERVER) {
                catalog = config.getDatabaseName();
                schema = schema != null ? schema : "dbo";
            }

            List<TableSchemaVO.Column> columns = new ArrayList<>();
            try (ResultSet rs = meta.getColumns(catalog, schema, table.table, null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int nullable = rs.getInt("NULLABLE");
                    columns.add(TableSchemaVO.Column.builder()
                        .name(name)
                        .type(typeName)
                        .nullable(nullable == DatabaseMetaData.columnNullable)
                        .build());
                }
            }
            return columns;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取表结构失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYNC_FAILED, "读取表结构失败: " + e.getMessage());
        }
    }

    private String buildJdbcUrl(SyncConfig config) {
        DbType type = DbType.from(config.getDbType());
        String host = config.getHost();
        Integer port = config.getPort();
        String db = config.getDatabaseName();

        if (type == DbType.MYSQL) {
            return String.format(Locale.ROOT,
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                host, port, db);
        }
        if (type == DbType.POSTGRESQL) {
            return String.format(Locale.ROOT, "jdbc:postgresql://%s:%d/%s", host, port, db);
        }
        if (type == DbType.SQLSERVER) {
            return String.format(Locale.ROOT, "jdbc:sqlserver://%s:%d;databaseName=%s", host, port, db);
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的数据库类型: " + config.getDbType());
    }

    private enum DbType {
        MYSQL,
        POSTGRESQL,
        SQLSERVER;

        static DbType from(String v) {
            if (v == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "数据库类型不能为空");
            }
            String s = v.trim().toLowerCase(Locale.ROOT);
            if ("mysql".equals(s)) {
                return MYSQL;
            }
            if ("postgresql".equals(s) || "postgres".equals(s)) {
                return POSTGRESQL;
            }
            if ("sqlserver".equals(s) || "mssql".equals(s)) {
                return SQLSERVER;
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的数据库类型: " + v);
        }
    }

    private static class QualifiedTable {
        private final String schema;
        private final String table;

        private QualifiedTable(String schema, String table) {
            this.schema = schema;
            this.table = table;
        }

        static QualifiedTable parse(String tableName) {
            if (tableName == null || tableName.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "表名不能为空");
            }
            String trimmed = tableName.trim();
            String[] parts = trimmed.split("\\.");
            if (parts.length == 1) {
                return new QualifiedTable(null, trimmed);
            }
            if (parts.length == 2) {
                return new QualifiedTable(parts[0], parts[1]);
            }
            // 兼容多段，最后一段作为表名，其余拼成schema
            String table = parts[parts.length - 1];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(parts[i]);
            }
            return new QualifiedTable(sb.toString(), table);
        }
    }
}

