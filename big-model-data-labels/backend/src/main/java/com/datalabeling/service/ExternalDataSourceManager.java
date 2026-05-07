package com.datalabeling.service;

import com.datalabeling.config.datasource.DataSourceConfig;
import com.datalabeling.config.datasource.DataSourceContextHolder;
import com.datalabeling.entity.SyncConfig;
import com.datalabeling.repository.SyncConfigRepository;
import com.datalabeling.util.OracleConnectionUrlBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.ref.SoftReference;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部数据源连接池管理器
 * 负责管理外部数据源的创建、销毁和缓存
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Service
@Slf4j
public class ExternalDataSourceManager implements DisposableBean {

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    @Autowired(required = false)
    private DataSourceConfig.ExternalDataSourceProperties properties;

    /**
     * 缓存外部数据源（使用软引用，内存不足时自动释放）
     * Key: syncConfigId, Value: SoftReference<DataSource>
     */
    private final Map<Integer, SoftReference<DataSource>> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 获取外部数据源（从缓存或创建新的）
     *
     * @param syncConfigId 同步配置ID
     * @return DataSource
     * @throws IllegalArgumentException 如果配置不存在
     */
    public DataSource getDataSource(Integer syncConfigId) {
        // 1. 尝试从缓存获取
        SoftReference<DataSource> cached = dataSourceCache.get(syncConfigId);
        if (cached != null && cached.get() != null) {
            log.debug("从缓存获取外部数据源: syncConfigId={}", syncConfigId);
            return cached.get();
        }

        // 2. 从数据库加载配置
        SyncConfig config = syncConfigRepository.findById(syncConfigId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + syncConfigId));

        // 3. 创建新数据源
        DataSource dataSource = createDataSource(config);

        // 4. 缓存数据源
        dataSourceCache.put(syncConfigId, new SoftReference<>(dataSource));

        log.info("创建并缓存外部数据源: syncConfigId={}, dbType={}",
                syncConfigId, config.getDbType());

        return dataSource;
    }

    /**
     * 创建外部数据源
     *
     * @param config 同步配置
     * @return DruidDataSource
     */
    private DataSource createDataSource(SyncConfig config) {
        com.alibaba.druid.pool.DruidDataSource dataSource = new com.alibaba.druid.pool.DruidDataSource();

        // 设置 JDBC URL
        String jdbcUrl = buildJdbcUrl(config);
        dataSource.setUrl(jdbcUrl);

        // 设置用户名和密码（密码需要解密）
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(decryptPassword(config.getPasswordEncrypted()));

        // 设置驱动类名
        dataSource.setDriverClassName(getDriverClassName(config.getDbType()));

        // 设置连接池配置
        if (properties != null) {
            dataSource.setInitialSize(properties.getInitialSize());
            dataSource.setMinIdle(properties.getMinIdle());
            dataSource.setMaxActive(properties.getMaxActive());
            dataSource.setMaxWait(properties.getMaxWait());
            dataSource.setTimeBetweenEvictionRunsMillis(
                    properties.getTimeBetweenEvictionRunsMillis());
            dataSource.setMinEvictableIdleTimeMillis(
                    properties.getMinEvictableIdleTimeMillis());
            dataSource.setTestWhileIdle(properties.isTestWhileIdle());
            dataSource.setTestOnBorrow(properties.isTestOnBorrow());
            dataSource.setTestOnReturn(properties.isTestOnReturn());
            dataSource.setPoolPreparedStatements(properties.isPoolPreparedStatements());
            dataSource.setMaxPoolPreparedStatementPerConnectionSize(
                    properties.getMaxPoolPreparedStatementPerConnectionSize());
        } else {
            // 默认配置
            dataSource.setInitialSize(1);
            dataSource.setMinIdle(1);
            dataSource.setMaxActive(5);
            dataSource.setMaxWait(60000);
        }

        // 设置验证查询
        String validationQuery = getValidationQuery(config.getDbType());
        dataSource.setValidationQuery(validationQuery);
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);

        log.info("创建外部数据源: dbType={}, url={}", config.getDbType(),
                maskJdbcUrl(jdbcUrl));

        return dataSource;
    }

    /**
     * 构建 JDBC URL
     *
     * @param config 同步配置
     * @return JDBC URL
     */
    private String buildJdbcUrl(SyncConfig config) {
        String dbType = config.getDbType().toLowerCase();

        switch (dbType) {
            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
                        config.getHost(), config.getPort(), config.getDatabaseName());

            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s",
                        config.getHost(), config.getPort(), config.getDatabaseName());

            case "sqlserver":
                return String.format("jdbc:sqlserver://%s:%d;databaseName=%s",
                        config.getHost(), config.getPort(), config.getDatabaseName());

            case "oracle":
                // 使用 Oracle 连接 URL 构建器
                return OracleConnectionUrlBuilder.buildUrl(
                        config.getConnectionMode(),
                        config.getHost(),
                        config.getPort(),
                        config.getOracleSid(),
                        config.getOracleServiceName()
                );

            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + config.getDbType());
        }
    }

    /**
     * 获取驱动类名
     *
     * @param dbType 数据库类型
     * @return 驱动类名
     */
    private String getDriverClassName(String dbType) {
        String type = dbType.toLowerCase();

        if (properties != null) {
            switch (type) {
                case "mysql":
                    return properties.getMysqlDriverClassName();
                case "oracle":
                    return properties.getOracleDriverClassName();
                case "postgresql":
                    return properties.getPostgresqlDriverClassName();
                case "sqlserver":
                    return properties.getSqlserverDriverClassName();
            }
        }

        // 默认驱动类名
        switch (type) {
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "oracle":
                return "oracle.jdbc.OracleDriver";
            case "postgresql":
                return "org.postgresql.Driver";
            case "sqlserver":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
    }

    /**
     * 获取验证查询语句
     *
     * @param dbType 数据库类型
     * @return 验证查询语句
     */
    private String getValidationQuery(String dbType) {
        if (properties != null && "oracle".equalsIgnoreCase(dbType)) {
            return properties.getOracleValidationQuery();
        }
        return "oracle".equalsIgnoreCase(dbType) ? "SELECT 1 FROM DUAL" : "SELECT 1";
    }

    /**
     * 解密密码
     * TODO: 集成现有的密码解密服务
     *
     * @param encryptedPassword 加密后的密码
     * @return 明文密码
     */
    private String decryptPassword(String encryptedPassword) {
        // TODO: 使用 SyncCryptoService 进行解密
        // 暂时返回原文，待后续集成
        return encryptedPassword;
    }

    /**
     * 掩码 JDBC URL（用于日志输出）
     *
     * @param jdbcUrl JDBC URL
     * @return 掩码后的 URL
     */
    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        // 隐藏密码部分
        return jdbcUrl.replaceAll("password=[^&;]*", "password=***");
    }

    /**
     * 测试连接
     *
     * @param syncConfigId 同步配置ID
     * @return 测试是否成功
     */
    public boolean testConnection(Integer syncConfigId) {
        try (Connection conn = getDataSource(syncConfigId).getConnection()) {
            boolean isValid = conn.isValid(5); // 5秒超时

            // 更新测试状态
            SyncConfig config = syncConfigRepository.findById(syncConfigId).orElse(null);
            if (config != null) {
                config.setConnectionTestStatus(isValid ? "success" : "failed");
                config.setConnectionTestTime(LocalDateTime.now());
                syncConfigRepository.save(config);
            }

            log.info("数据源连接测试: syncConfigId={}, result={}",
                    syncConfigId, isValid ? "成功" : "失败");

            return isValid;
        } catch (Exception e) {
            log.error("数据源连接测试失败: syncConfigId=" + syncConfigId, e);

            // 更新失败状态
            SyncConfig config = syncConfigRepository.findById(syncConfigId).orElse(null);
            if (config != null) {
                config.setConnectionTestStatus("failed");
                config.setConnectionTestTime(LocalDateTime.now());
                syncConfigRepository.save(config);
            }

            return false;
        }
    }

    /**
     * 清理指定数据源的缓存
     *
     * @param syncConfigId 同步配置ID
     */
    public void evictDataSource(Integer syncConfigId) {
        SoftReference<DataSource> cached = dataSourceCache.remove(syncConfigId);
        if (cached != null) {
            DataSource ds = cached.get();
            if (ds instanceof com.alibaba.druid.pool.DruidDataSource) {
                ((com.alibaba.druid.pool.DruidDataSource) ds).close();
                log.info("关闭并清理外部数据源: syncConfigId={}", syncConfigId);
            }
        }
    }

    /**
     * 清理所有外部数据源缓存
     */
    public void evictAll() {
        log.info("开始清理所有外部数据源缓存，共 {} 个", dataSourceCache.size());

        dataSourceCache.forEach((id, ref) -> {
            DataSource ds = ref.get();
            if (ds instanceof com.alibaba.druid.pool.DruidDataSource) {
                try {
                    ((com.alibaba.druid.pool.DruidDataSource) ds).close();
                } catch (Exception e) {
                    log.warn("关闭数据源时出错: syncConfigId={}", id, e);
                }
            }
        });

        int size = dataSourceCache.size();
        dataSourceCache.clear();

        log.info("清理完成，共清理 {} 个外部数据源", size);
    }

    /**
     * 应用关闭前清理资源
     */
    @Override
    public void destroy() {
        evictAll();
    }

    /**
     * 获取缓存的数据源数量
     *
     * @return 缓存数量
     */
    public int getCacheSize() {
        return dataSourceCache.size();
    }

    /**
     * 检查数据源是否已缓存
     *
     * @param syncConfigId 同步配置ID
     * @return true 表示已缓存
     */
    public boolean isCached(Integer syncConfigId) {
        SoftReference<DataSource> cached = dataSourceCache.get(syncConfigId);
        return cached != null && cached.get() != null;
    }
}
