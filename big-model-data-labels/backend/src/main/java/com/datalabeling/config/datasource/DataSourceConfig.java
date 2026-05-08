package com.datalabeling.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 多数据源配置类
 * 配置主数据源和动态数据源
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /**
     * 主数据源（本项目的 MySQL 数据库）
     *
     * @return DruidDataSource
     */
    @Primary
    @Bean(name = "mainDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource mainDataSource() {
        log.info("初始化主数据源: {}", DataSourceContextHolder.MAIN_DATA_SOURCE);
        return DataSourceBuilder.create().type(DruidDataSource.class).build();
    }

    /**
     * 动态数据源
     * 包含主数据源和所有外部数据源的路由
     *
     * @param mainDataSource 主数据源
     * @return DynamicDataSource
     */
    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource(
            @Qualifier("mainDataSource") DataSource mainDataSource) {
        log.info("初始化动态数据源");

        Map<Object, Object> targetDataSources = new HashMap<>();
        // 添加主数据源
        targetDataSources.put(DataSourceContextHolder.MAIN_DATA_SOURCE, mainDataSource);

        DynamicDataSource dynamicDataSource = new DynamicDataSource(mainDataSource, targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(mainDataSource);

        log.info("动态数据源初始化完成，当前包含 {} 个数据源", targetDataSources.size());
        return dynamicDataSource;
    }

    /**
     * 外部数据源配置属性
     * 从 application.yml 中读取外部数据源的连接池配置
     */
    @org.springframework.boot.context.properties.ConfigurationProperties(prefix = "external.datasource")
    public static class ExternalDataSourceProperties {
        /**
         * 通用连接池配置
         */
        private int initialSize = 1;
        private int maxActive = 5;
        private int minIdle = 1;
        private long maxWait = 60000;
        private long timeBetweenEvictionRunsMillis = 60000;
        private long minEvictableIdleTimeMillis = 300000;
        private String validationQuery = "SELECT 1";
        private boolean testWhileIdle = true;
        private boolean testOnBorrow = false;
        private boolean testOnReturn = false;
        private boolean poolPreparedStatements = true;
        private int maxPoolPreparedStatementPerConnectionSize = 20;

        // MySQL 特定配置
        private String mysqlDriverClassName = "com.mysql.cj.jdbc.Driver";

        // Oracle 特定配置
        private String oracleDriverClassName = "oracle.jdbc.OracleDriver";
        private String oracleValidationQuery = "SELECT 1 FROM DUAL";

        // PostgreSQL 特定配置
        private String postgresqlDriverClassName = "org.postgresql.Driver";

        // SQL Server 特定配置
        private String sqlserverDriverClassName = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

        public int getInitialSize() {
            return initialSize;
        }

        public void setInitialSize(int initialSize) {
            this.initialSize = initialSize;
        }

        public int getMaxActive() {
            return maxActive;
        }

        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }

        public long getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(long maxWait) {
            this.maxWait = maxWait;
        }

        public long getTimeBetweenEvictionRunsMillis() {
            return timeBetweenEvictionRunsMillis;
        }

        public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
            this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        }

        public long getMinEvictableIdleTimeMillis() {
            return minEvictableIdleTimeMillis;
        }

        public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
            this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        }

        public String getValidationQuery() {
            return validationQuery;
        }

        public void setValidationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
        }

        public boolean isTestWhileIdle() {
            return testWhileIdle;
        }

        public void setTestWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
        }

        public boolean isTestOnBorrow() {
            return testOnBorrow;
        }

        public void setTestOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
        }

        public boolean isTestOnReturn() {
            return testOnReturn;
        }

        public void setTestOnReturn(boolean testOnReturn) {
            this.testOnReturn = testOnReturn;
        }

        public boolean isPoolPreparedStatements() {
            return poolPreparedStatements;
        }

        public void setPoolPreparedStatements(boolean poolPreparedStatements) {
            this.poolPreparedStatements = poolPreparedStatements;
        }

        public int getMaxPoolPreparedStatementPerConnectionSize() {
            return maxPoolPreparedStatementPerConnectionSize;
        }

        public void setMaxPoolPreparedStatementPerConnectionSize(int maxPoolPreparedStatementPerConnectionSize) {
            this.maxPoolPreparedStatementPerConnectionSize = maxPoolPreparedStatementPerConnectionSize;
        }

        public String getMysqlDriverClassName() {
            return mysqlDriverClassName;
        }

        public void setMysqlDriverClassName(String mysqlDriverClassName) {
            this.mysqlDriverClassName = mysqlDriverClassName;
        }

        public String getOracleDriverClassName() {
            return oracleDriverClassName;
        }

        public void setOracleDriverClassName(String oracleDriverClassName) {
            this.oracleDriverClassName = oracleDriverClassName;
        }

        public String getOracleValidationQuery() {
            return oracleValidationQuery;
        }

        public void setOracleValidationQuery(String oracleValidationQuery) {
            this.oracleValidationQuery = oracleValidationQuery;
        }

        public String getPostgresqlDriverClassName() {
            return postgresqlDriverClassName;
        }

        public void setPostgresqlDriverClassName(String postgresqlDriverClassName) {
            this.postgresqlDriverClassName = postgresqlDriverClassName;
        }

        public String getSqlserverDriverClassName() {
            return sqlserverDriverClassName;
        }

        public void setSqlserverDriverClassName(String sqlserverDriverClassName) {
            this.sqlserverDriverClassName = sqlserverDriverClassName;
        }
    }
}
