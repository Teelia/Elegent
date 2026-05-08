package com.datalabeling.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

/**
 * 动态数据源路由器
 * 基于 Spring AbstractRoutingDataSource 实现，支持在运行时动态切换数据源
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    /**
     * 目标数据源的解析结果缓存
     */
    private Map<Object, Object> targetDataSources;

    /**
     * 默认目标数据源
     */
    private Object defaultTargetDataSource;

    /**
     * 构造函数
     *
     * @param defaultTargetDataSource 默认目标数据源
     * @param targetDataSources      目标数据源映射
     */
    public DynamicDataSource(DataSource defaultTargetDataSource,
                             Map<Object, Object> targetDataSources) {
        this.defaultTargetDataSource = defaultTargetDataSource;
        this.targetDataSources = targetDataSources;
        super.setDefaultTargetDataSource(defaultTargetDataSource);
        super.setTargetDataSources(targetDataSources);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String key = DataSourceContextHolder.getDataSourceKey();
        if (log.isDebugEnabled()) {
            log.debug("当前数据源标识: {}", key);
        }
        return key;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // 如果强制使用外部数据源，直接路由
        if (DataSourceContextHolder.isForceExternal()) {
            String key = DataSourceContextHolder.getDataSourceKey();
            if (key != null && key.startsWith(DataSourceContextHolder.EXTERNAL_DATA_SOURCE_PREFIX)) {
                return getExternalConnection(key);
            }
        }
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // 如果强制使用外部数据源，直接路由
        if (DataSourceContextHolder.isForceExternal()) {
            String key = DataSourceContextHolder.getDataSourceKey();
            if (key != null && key.startsWith(DataSourceContextHolder.EXTERNAL_DATA_SOURCE_PREFIX)) {
                return getExternalConnection(key);
            }
        }
        return super.getConnection(username, password);
    }

    /**
     * 获取外部数据源的连接
     *
     * @param key 外部数据源标识
     * @return 数据库连接
     * @throws SQLException 如果获取连接失败
     */
    private Connection getExternalConnection(String key) throws SQLException {
        DataSource dataSource = determineTargetDataSource(key);
        if (dataSource == null) {
            throw new SQLException("无法找到数据源: " + key);
        }
        return dataSource.getConnection();
    }

    /**
     * 根据查找键确定目标数据源
     *
     * @param key 查找键
     * @return 目标数据源
     */
    private DataSource determineTargetDataSource(Object key) {
        if (this.targetDataSources == null) {
            return null;
        }
        Object dataSource = this.targetDataSources.get(key);
        if (dataSource == null) {
            return null;
        }
        return (DataSource) dataSource;
    }

    /**
     * 添加新的外部数据源
     *
     * @param key        数据源标识
     * @param dataSource 数据源
     */
    public void addDataSource(String key, DataSource dataSource) {
        if (this.targetDataSources != null) {
            this.targetDataSources.put(key, dataSource);
            super.afterPropertiesSet(); // 重新初始化
            log.info("添加外部数据源: {}", key);
        }
    }

    /**
     * 移除外部数据源
     *
     * @param key 数据源标识
     */
    public void removeDataSource(String key) {
        if (this.targetDataSources != null) {
            Object removed = this.targetDataSources.remove(key);
            if (removed != null) {
                // 关闭数据源
                if (removed instanceof DruidDataSource) {
                    ((DruidDataSource) removed).close();
                }
                super.afterPropertiesSet(); // 重新初始化
                log.info("移除外部数据源: {}", key);
            }
        }
    }

    /**
     * 检查数据源是否存在
     *
     * @param key 数据源标识
     * @return true 表示存在
     */
    public boolean containsDataSource(String key) {
        return this.targetDataSources != null && this.targetDataSources.containsKey(key);
    }

    /**
     * 获取所有数据源的键集合
     *
     * @return 数据源键集合
     */
    public java.util.Set<Object> getDataSourceKeys() {
        if (this.targetDataSources == null) {
            return java.util.Collections.emptySet();
        }
        return this.targetDataSources.keySet();
    }
}
