# 外部数据源集成方案（更新版）

## 已明确的决策

基于用户需求和项目实际情况，以下决策已确定：

1. **表结构设计**: **方案 A** - 复用现有 `sync_configs` 表，扩展支持导入和导出双方向
2. **大数据量导入策略**: **方案 A** - 分批全量导入（MVP优先），每批1000行
3. **Oracle 连接配置**: **方案 B** - 支持多种模式（SID/ServiceName/TNS），包含多个 Oracle 驱动版本
4. **导入数据更新策略**: **方案 B** - 支持增量更新（基于时间戳）
5. **前端 UI 风格**: **方案 A** - 独立页面 + 导航菜单项

---

## 整体规划概述

### 项目目标

扩展数据标注平台，支持从外部 MySQL/Oracle 数据库直接导入数据创建数据集。核心特性包括：
- 数据源配置的统一管理（复用 sync_configs 表）
- 支持导入和导出两种数据流向
- 分批全量导入 + 增量更新能力
- Oracle 多种连接模式支持
- 独立的数据源管理页面

### 技术栈

- **后端**: Java 8 + Spring Boot 2.7 + Maven + JPA
- **前端**: Vue 3 + Element Plus
- **数据库**: MySQL 8.0+ (主数据库)
- **外部数据源**: MySQL 5.7+, Oracle 11g+
- **连接池**: Druid (现有)
- **Oracle 驱动**: ojdbc8, ojdbc10, ojdbc11 (多版本支持)

### 主要阶段

1. **阶段 1**: 数据库表结构改造与实体类调整（1-2天）
2. **阶段 2**: Oracle 多版本驱动配置（0.5天）
3. **阶段 3**: 动态数据源架构实现（2-3天）
4. **阶段 4**: 数据导入服务开发（3-4天）
5. **阶段 5**: 前端独立页面开发（3-4天）
6. **阶段 6**: 增量更新功能实现（2-3天）
7. **阶段 7**: 测试与优化（2-3天）

---

## 详细任务分解

### 阶段 1：数据库表结构改造与实体类调整

#### 任务 1.1：修改 sync_configs 表结构

**目标**: 扩展现有表结构以支持数据导入和 Oracle 连接

**输入**: 现有 sync_configs 表结构

**输出**: ALTER TABLE SQL 语句

**涉及文件**:
- 数据库迁移脚本: `backend/src/main/resources/db/migration/VXX__alter_sync_configs_for_import.sql`

**DDL 语句**:

```sql
-- 1. 新增数据方向字段（区分导入/导出）
ALTER TABLE sync_configs
ADD COLUMN direction VARCHAR(10) DEFAULT 'export' COMMENT '数据方向: import=导入, export=导出';

-- 2. 新增 Oracle 专用字段
ALTER TABLE sync_configs
ADD COLUMN oracle_sid VARCHAR(100) COMMENT 'Oracle SID(当connection_mode=sid时使用)',
ADD COLUMN oracle_service_name VARCHAR(100) COMMENT 'Oracle Service Name(当connection_mode=service_name时使用)',
ADD COLUMN connection_mode VARCHAR(20) DEFAULT 'standard' COMMENT '连接模式: standard=标准, sid=SID模式, service_name=ServiceName模式, tns=TNS模式';

-- 3. 新增导入专用字段
ALTER TABLE sync_configs
ADD COLUMN import_query TEXT COMMENT '自定义SQL查询条件(仅导入时使用)',
ADD COLUMN last_import_time TIMESTAMP NULL COMMENT '上次导入时间(用于增量更新)',
ADD COLUMN import_status VARCHAR(20) COMMENT '导入状态: pending/importing/completed/failed',
ADD COLUMN connection_test_status VARCHAR(20) COMMENT '连接测试状态: success/failed/unknown',
ADD COLUMN connection_test_time TIMESTAMP NULL COMMENT '最后测试连接时间';

-- 4. 调整现有字段为可选
ALTER TABLE sync_configs
MODIFY COLUMN table_name VARCHAR(100) COMMENT '表名(导入/导出时需要)',
MODIFY COLUMN field_mappings JSON COMMENT '字段映射(导出时需要)';

-- 5. 新增索引
ALTER TABLE sync_configs
ADD INDEX idx_direction (direction),
ADD INDEX idx_connection_test (connection_test_status);
```

**预估工作量**: 2-3小时

---

#### 任务 1.2：调整 SyncConfig 实体类

**目标**: 更新实体类以支持新增字段和导入方向

**输入**: 更新后的数据库表结构

**输出**: 修改后的 SyncConfig.java

**涉及文件**:
- `backend/src/main/java/com/datalabeling/entity/SyncConfig.java`

**主要修改点**:

```java
/**
 * 数据库同步配置实体（支持导入和导出）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sync_configs", indexes = {
    @Index(name = "idx_sync_configs_user_id", columnList = "user_id"),
    @Index(name = "idx_direction", columnList = "direction"),
    @Index(name = "idx_connection_test", columnList = "connection_test_status")
})
public class SyncConfig extends BaseEntity {

    // ... 现有字段 ...

    /**
     * 数据方向：import=导入, export=导出
     */
    @Column(name = "direction", length = 10)
    private String direction = "export";

    /**
     * 数据库类型：postgresql, mysql, sqlserver, oracle
     */
    @NotBlank(message = "数据库类型不能为空")
    @Size(max = 20, message = "数据库类型长度不能超过20")
    @Column(name = "db_type", nullable = false, length = 20)
    private String dbType;

    /**
     * 目标表名（导入和导出都需要）
     */
    @Size(max = 100, message = "表名长度不能超过100")
    @Column(name = "table_name", length = 100)
    private String tableName;

    /**
     * 字段映射（JSON格式，仅导出时需要）
     * 格式：{"文件列名": "数据库字段名"}
     */
    @Convert(converter = JsonConverter.class)
    @Column(name = "field_mappings", columnDefinition = "JSON")
    private Map<String, Object> fieldMappings;

    // ========== Oracle 专用字段 ==========

    /**
     * Oracle SID（当 connection_mode=sid 时使用）
     */
    @Size(max = 100, message = "SID长度不能超过100")
    @Column(name = "oracle_sid", length = 100)
    private String oracleSid;

    /**
     * Oracle Service Name（当 connection_mode=service_name 时使用）
     */
    @Size(max = 100, message = "Service Name长度不能超过100")
    @Column(name = "oracle_service_name", length = 100)
    private String oracleServiceName;

    /**
     * 连接模式：standard=标准, sid=SID模式, service_name=ServiceName模式, tns=TNS模式
     */
    @Column(name = "connection_mode", length = 20)
    private String connectionMode = "standard";

    // ========== 导入专用字段 ==========

    /**
     * 自定义 SQL 查询条件（仅导入时使用）
     * 示例：created_at > '2024-01-01' AND status = 'active'
     */
    @Lob
    @Column(name = "import_query", columnDefinition = "TEXT")
    private String importQuery;

    /**
     * 上次导入时间（用于增量更新）
     */
    @Column(name = "last_import_time")
    private LocalDateTime lastImportTime;

    /**
     * 导入状态：pending, importing, completed, failed
     */
    @Column(name = "import_status", length = 20)
    private String importStatus;

    /**
     * 连接测试状态：success, failed, unknown
     */
    @Column(name = "connection_test_status", length = 20)
    private String connectionTestStatus;

    /**
     * 最后测试连接时间
     */
    @Column(name = "connection_test_time")
    private LocalDateTime connectionTestTime;
}
```

**预估工作量**: 2小时

---

#### 任务 1.3：扩展 Dataset 实体支持数据源追踪

**目标**: 为 Dataset 实体添加数据源类型和关联字段

**输入**: 现有 Dataset 实体

**输出**: 修改后的 Dataset 实体

**涉及文件**:
- `backend/src/main/java/com/datalabeling/entity/Dataset.java`
- 数据库迁移脚本

**DDL 语句**:

```sql
ALTER TABLE datasets
ADD COLUMN source_type VARCHAR(20) DEFAULT 'file' COMMENT '数据来源: file=文件, database=数据库',
ADD COLUMN external_source_id INT COMMENT '外部数据源ID(当source_type=database时)',
ADD COLUMN import_query TEXT COMMENT '导入时使用的SQL查询条件',
ADD INDEX idx_source_type (source_type),
ADD INDEX idx_external_source_id (external_source_id);
```

**实体类修改**:

```java
/**
 * 数据来源类型：file=文件, database=数据库
 */
@Column(name = "source_type", length = 20)
private String sourceType = "file";

/**
 * 外部数据源ID（当 source_type=database 时）
 */
@Column(name = "external_source_id")
private Integer externalSourceId;

/**
 * 导入时使用的 SQL 查询条件
 */
@Lob
@Column(name = "import_query", columnDefinition = "TEXT")
private String importQuery;
```

**预估工作量**: 1-2小时

---

### 阶段 2：Oracle 多版本驱动配置

#### 任务 2.1：添加 Oracle JDBC 驱动依赖

**目标**: 在 pom.xml 中添加多个版本的 Oracle 驱动

**输入**: Oracle 版本兼容性要求

**输出**: 更新后的 pom.xml

**涉及文件**:
- `backend/pom.xml`

**Maven 依赖配置**:

```xml
<!-- Oracle JDBC Drivers (多版本支持) -->

<!-- ojdbc8: Oracle 12c+ (推荐，Java 8) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>19.8.0.0</version>
    <optional>true</optional>
</dependency>

<!-- ojdbc10: Oracle 19c+ (Java 10+) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc10</artifactId>
    <version>19.8.0.0</version>
    <optional>true</optional>
</dependency>

<!-- ojdbc11: Oracle 21c+ (Java 11+) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>21.3.0.0</version>
    <optional>true</optional>
</dependency>

<!-- Oracle Universal Connection Pool (可选，提供更好的连接池性能) -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ucp</artifactId>
    <version>19.8.0.0</version>
    <optional>true</optional>
</dependency>
```

**注意事项**:
- 所有驱动设置为 `optional=true`，避免传递依赖冲突
- 项目使用 Java 8，默认使用 ojdbc8
- 根据目标 Oracle 版本动态选择驱动

**预估工作量**: 0.5小时

---

#### 任务 2.2：实现 Oracle 连接 URL 构建器

**目标**: 根据连接模式动态构建 Oracle JDBC URL

**输入**: 连接模式和配置参数

**输出**: OracleConnectionUrlBuilder 工具类

**涉及文件**:
- `backend/src/main/java/com/datalabeling/util/OracleConnectionUrlBuilder.java`

**实现方案**:

```java
/**
 * Oracle 连接 URL 构建器
 * 支持多种连接模式：标准、SID、ServiceName、TNS
 */
public class OracleConnectionUrlBuilder {

    /**
     * 构建 Oracle JDBC URL
     *
     * @param mode   连接模式：standard, sid, service_name, tns
     * @param host   主机地址
     * @param port   端口
     * @param sid    Oracle SID（mode=sid 时使用）
     * @param service Oracle Service Name（mode=service_name 时使用）
     * @return JDBC URL
     */
    public static String buildUrl(String mode, String host, Integer port,
                                   String sid, String service) {
        switch (mode.toLowerCase()) {
            case "sid":
                // 格式: jdbc:oracle:thin:@host:port:sid
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);

            case "service_name":
                // 格式: jdbc:oracle:thin:@//host:port/service_name
                return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, service);

            case "tns":
                // 格式: jdbc:oracle:thin:@TNS别名
                // TNS 模式需要配置 tnsnames.ora，这里简化处理
                return String.format("jdbc:oracle:thin:@%s", service);

            case "standard":
            default:
                // 默认使用 Service Name 模式
                if (StringUtils.isNotBlank(service)) {
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, service);
                } else if (StringUtils.isNotBlank(sid)) {
                    return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);
                } else {
                    throw new IllegalArgumentException(
                        "标准模式下必须提供 SID 或 Service Name");
                }
        }
    }

    /**
     * 根据目标 Oracle 版本选择合适的驱动类名
     *
     * @param oracleVersion 目标 Oracle 版本（如 "11g", "12c", "19c"）
     * @return 驱动类名
     */
    public static String selectDriverClass(String oracleVersion) {
        if (oracleVersion == null) {
            return "oracle.jdbc.OracleDriver"; // 默认驱动
        }

        // 根据版本选择驱动
        if (oracleVersion.startsWith("11")) {
            return "oracle.jdbc.OracleDriver"; // ojdbc8
        } else if (oracleVersion.startsWith("12")) {
            return "oracle.jdbc.OracleDriver"; // ojdbc8
        } else if (oracleVersion.startsWith("19")) {
            return "oracle.jdbc.OracleDriver"; // ojdbc10
        } else if (oracleVersion.startsWith("21")) {
            return "oracle.jdbc.OracleDriver"; // ojdbc11
        }

        return "oracle.jdbc.OracleDriver";
    }
}
```

**预估工作量**: 2小时

---

### 阶段 3：动态数据源架构实现

#### 任务 3.1：实现动态数据源路由器

**目标**: 创建基于 ThreadLocal 的数据源路由器

**输入**: Spring 动态数据源最佳实践

**输出**: DynamicDataSource 相关类

**涉及文件**:
- `backend/src/main/java/com/datalabeling/config/datasource/DataSourceContextHolder.java`
- `backend/src/main/java/com/datalabeling/config/datasource/DynamicDataSource.java`

**实现方案**:

```java
/**
 * 数据源上下文持有者（ThreadLocal）
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> forceExternal = new ThreadLocal<>();

    /**
     * 设置数据源标识
     * @param key 数据源标识（格式：external_{syncConfigId}）
     */
    public static void setDataSourceKey(String key) {
        contextHolder.set(key);
    }

    /**
     * 获取当前数据源标识
     */
    public static String getDataSourceKey() {
        return contextHolder.get();
    }

    /**
     * 清除数据源标识
     */
    public static void clearDataSourceKey() {
        contextHolder.remove();
        forceExternal.remove();
    }

    /**
     * 标记强制使用外部数据源
     */
    public static void setForceExternal(boolean force) {
        forceExternal.set(force);
    }

    /**
     * 是否强制使用外部数据源
     */
    public static Boolean isForceExternal() {
        Boolean force = forceExternal.get();
        return force != null && force;
    }
}

/**
 * 动态数据源路由器
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceKey();
    }

    @Override
    public Connection getConnection() throws SQLException {
        // 如果强制使用外部数据源，直接路由
        if (DataSourceContextHolder.isForceExternal()) {
            String key = DataSourceContextHolder.getDataSourceKey();
            if (key != null && key.startsWith("external_")) {
                return determineTargetDataSource(key).getConnection();
            }
        }
        return super.getConnection();
    }

    private DataSource determineTargetDataSource(String key) {
        DataSourceLookupResult result = this.getResolvedDataSource(key);
        if (result == null) {
            throw new IllegalStateException("Cannot find datasource for key: " + key);
        }
        return result.getDataSource();
    }
}
```

**预估工作量**: 3小时

---

#### 任务 3.2：配置多数据源 Bean

**目标**: 创建主数据源和动态数据源配置类

**输入**: 数据库连接配置

**输出**: DataSourceConfig 配置类

**涉及文件**:
- `backend/src/main/java/com/datalabeling/config/datasource/DataSourceConfig.java`
- `backend/src/main/resources/application.yml`

**配置类实现**:

```java
@Configuration
public class DataSourceConfig {

    @Primary
    @Bean(name = "mainDataSource")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource mainDataSource() {
        return DruidDataSourceBuilder.create().build();
    }

    @Bean(name = "dynamicDataSource")
    public DataSource dynamicDataSource(
            @Qualifier("mainDataSource") DataSource mainDataSource) {
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("main", mainDataSource);
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setDefaultTargetDataSource(mainDataSource);
        return dynamicDataSource;
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.druid")
    public DataSourceFactory druidDataSourceFactory() {
        return new DruidDataSourceFactory();
    }
}
```

**配置文件扩展**:

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      # 主数据源配置（现有）
      url: jdbc:mysql://localhost:3306/data_labeling
      username: root
      password: your_password

# 外部数据源连接池配置（动态创建）
external:
  datasource:
    # 通用配置
    initial-size: 1
    max-active: 5
    min-idle: 1
    max-wait: 60000
    time-between-eviction-runs-millis: 60000
    min-evictable-idle-time-millis: 300000
    validation-query: SELECT 1
    test-while-idle: true
    test-on-borrow: false
    test-on-return: false
    pool-prepared-statements: true
    max-pool-prepared-statement-per-connection-size: 20

    # MySQL 特定配置
    mysql:
      driver-class-name: com.mysql.cj.jdbc.Driver

    # Oracle 特定配置
    oracle:
      driver-class-name: oracle.jdbc.OracleDriver
      # Oracle 连接验证查询
      validation-query: SELECT 1 FROM DUAL
```

**预估工作量**: 3小时

---

#### 任务 3.3：实现外部数据源连接池管理器

**目标**: 管理外部数据源的连接池创建、销毁和缓存

**输入**: 数据源配置信息

**输出**: ExternalDataSourceManager 服务类

**涉及文件**:
- `backend/src/main/java/com/datalabeling/service/ExternalDataSourceManager.java`

**实现方案**:

```java
@Service
@Slf4j
public class ExternalDataSourceManager {

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    @Autowired
    private DataSourceFactory dataSourceFactory;

    // 缓存外部数据源（使用软引用，内存不足时自动释放）
    private final Map<Integer, SoftReference<DataSource>> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 获取外部数据源（从缓存或创建新的）
     *
     * @param syncConfigId 同步配置ID
     * @return DataSource
     */
    public DataSource getDataSource(Integer syncConfigId) {
        // 1. 尝试从缓存获取
        SoftReference<DataSource> cached = dataSourceCache.get(syncConfigId);
        if (cached != null && cached.get() != null) {
            return cached.get();
        }

        // 2. 从数据库加载配置
        SyncConfig config = syncConfigRepository.findById(syncConfigId)
            .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        // 3. 创建新数据源
        DataSource dataSource = createDataSource(config);

        // 4. 缓存数据源
        dataSourceCache.put(syncConfigId, new SoftReference<>(dataSource));

        return dataSource;
    }

    /**
     * 创建外部数据源
     */
    private DataSource createDataSource(SyncConfig config) {
        DruidDataSource dataSource = new DruidDataSource();

        // 基本配置
        dataSource.setUrl(buildJdbcUrl(config));
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(decryptPassword(config.getPasswordEncrypted()));

        // 驱动配置
        dataSource.setDriverClassName(getDriverClassName(config.getDbType()));

        // 连接池配置（从 application.yml 读取或使用默认值）
        dataSource.setInitialSize(1);
        dataSource.setMinIdle(1);
        dataSource.setMaxActive(5);
        dataSource.setMaxWait(60000);

        // 验证配置
        if ("oracle".equals(config.getDbType())) {
            dataSource.setValidationQuery("SELECT 1 FROM DUAL");
        } else {
            dataSource.setValidationQuery("SELECT 1");
        }
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);

        return dataSource;
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(SyncConfig config) {
        switch (config.getDbType().toLowerCase()) {
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
     * 测试连接
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

            return isValid;
        } catch (Exception e) {
            log.error("数据源连接测试失败", e);
            return false;
        }
    }

    /**
     * 清理指定数据源的缓存
     */
    public void evictDataSource(Integer syncConfigId) {
        SoftReference<DataSource> cached = dataSourceCache.remove(syncConfigId);
        if (cached != null) {
            DataSource ds = cached.get();
            if (ds instanceof DruidDataSource) {
                ((DruidDataSource) ds).close();
            }
        }
    }

    /**
     * 清理所有外部数据源缓存
     */
    public void evictAll() {
        dataSourceCache.forEach((id, ref) -> {
            DataSource ds = ref.get();
            if (ds instanceof DruidDataSource) {
                ((DruidDataSource) ds).close();
            }
        });
        dataSourceCache.clear();
    }
}
```

**预估工作量**: 4小时

---

### 阶段 4：数据导入服务开发

#### 任务 4.1：实现数据库/表列表浏览

**目标**: 获取数据源中的数据库和表列表

**输入**: 数据源 ID

**输出**: 数据库和表树形结构

**涉及文件**:
- `backend/src/main/java/com/datalabeling/service/ExternalDataSourceService.java`
- `backend/src/main/java/com/datalabeling/dto/response/DatabaseExplorerVO.java`

**DTO 定义**:

```java
/**
 * 数据库浏览器响应
 */
@Data
public class DatabaseExplorerVO {
    private List<DatabaseItem> databases;

    @Data
    public static class DatabaseItem {
        private String name;
        private List<TableItem> tables;
    }

    @Data
    public static class TableItem {
        private String name;
        private String type; // TABLE, VIEW
        private Long rowCount; // 估算的行数
    }
}
```

**服务实现**:

```java
@Service
@Slf4j
public class ExternalDataSourceService {

    @Autowired
    private ExternalDataSourceManager dataSourceManager;

    @Autowired
    private SyncConfigRepository syncConfigRepository;

    /**
     * 浏览数据库和表
     */
    public DatabaseExplorerVO exploreDatabases(Integer syncConfigId) {
        SyncConfig config = syncConfigRepository.findById(syncConfigId)
            .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

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

            return result;

        } catch (SQLException e) {
            log.error("浏览数据库失败", e);
            throw new RuntimeException("浏览数据库失败: " + e.getMessage());
        }
    }

    private List<DatabaseExplorerVO.DatabaseItem> listDatabases(
            DatabaseMetaData metaData, SyncConfig config) throws SQLException {

        List<DatabaseExplorerVO.DatabaseItem> databases = new ArrayList<>();

        // 如果指定了数据库，只查询该数据库
        String databasePattern = config.getDatabaseName();

        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                String dbName = rs.getString("TABLE_CAT");

                if (databasePattern != null && !dbName.equals(databasePattern)) {
                    continue;
                }

                DatabaseExplorerVO.DatabaseItem db = new DatabaseExplorerVO.DatabaseItem();
                db.setName(dbName);
                db.setTables(listTables(metaData, dbName, null));
                databases.add(db);
            }
        }

        return databases;
    }

    private List<DatabaseExplorerVO.TableItem> listTables(
            DatabaseMetaData metaData, String catalog, String schemaPattern) throws SQLException {

        List<DatabaseExplorerVO.TableItem> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%",
                new String[]{"TABLE", "VIEW"})) {

            while (rs.next()) {
                DatabaseExplorerVO.TableItem table = new DatabaseExplorerVO.TableItem();
                table.setName(rs.getString("TABLE_NAME"));
                table.setType(rs.getString("TABLE_TYPE"));

                // 获取估算的行数
                tables.add(table);
            }
        }

        return tables;
    }

    // ... 其他数据库类型的类似实现 ...
}
```

**预估工作量**: 4小时

---

#### 任务 4.2：实现表数据预览

**目标**: 支持预览表的前 N 行数据（类似文件上传的预览）

**输入**: 数据源 ID, 表名, WHERE 条件

**输出**: 预览数据 JSON

**涉及文件**:
- `backend/src/main/java/com/datalabeling/dto/response/TablePreviewVO.java`

**DTO 定义**:

```java
@Data
public class TablePreviewVO {
    private String tableName;
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private Integer totalRows; // 总行数（估算）
    private String sql; // 实际执行的 SQL
}
```

**服务实现**:

```java
/**
 * 预览表数据（前20行）
 */
public TablePreviewVO previewTableData(Integer syncConfigId, String tableName,
                                       String whereClause) {

    SyncConfig config = syncConfigRepository.findById(syncConfigId)
        .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

    DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

    String sql = buildSelectSql(config, tableName, whereClause, 20);

    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        TablePreviewVO preview = new TablePreviewVO();
        preview.setTableName(tableName);
        preview.setSql(sql);

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

        // 获取总行数（异步查询，不阻塞）
        preview.setTotalRows(estimateRowCount(conn, tableName));

        return preview;

    } catch (SQLException e) {
        log.error("预览表数据失败", e);
        throw new RuntimeException("预览失败: " + e.getMessage());
    }
}

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
            sql += " LIMIT " + limit;
            break;
        case "oracle":
            // Oracle 使用 ROWNUM
            sql = "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
            break;
        case "sqlserver":
            sql += " SELECT TOP " + limit + " * FROM (" + sql + ") AS t";
            break;
    }

    return sql;
}
```

**预估工作量**: 3小时

---

#### 任务 4.3：实现分批全量导入

**目标**: 使用分批处理和批量插入导入数据

**输入**: 数据源 ID, 表名, WHERE 条件, 目标 Dataset ID

**输出**: 导入的行数和进度

**涉及文件**:
- `backend/src/main/java/com/datalabeling/service/DataImportService.java`
- `backend/src/main/java/com/datalabeling/config/AsyncImportConfig.java`

**配置类**:

```java
@Configuration
@EnableAsync
public class AsyncImportConfig {

    @Bean(name = "importTaskExecutor")
    public Executor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("data-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

**服务实现**:

```java
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
    private ApplicationEventPublisher eventPublisher;

    private static final int BATCH_SIZE = 1000;

    /**
     * 启动数据导入任务（异步）
     */
    @Async("importTaskExecutor")
    public void importData(Integer syncConfigId, Integer datasetId, String whereClause) {

        SyncConfig config = syncConfigRepository.findById(syncConfigId)
            .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

        Dataset dataset = datasetRepository.findById(datasetId)
            .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));

        // 更新状态
        config.setImportStatus("importing");
        syncConfigRepository.save(config);

        DataSource dataSource = dataSourceManager.getDataSource(syncConfigId);

        int totalImported = 0;
        int batchCount = 0;

        try (Connection conn = dataSource.getConnection()) {
            // 构建查询 SQL
            String sql = buildSelectSql(config, config.getTableName(), whereClause, -1);

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

                    // 更新 Dataset 列信息
                    dataset.setColumns(columns.stream()
                        .collect(Collectors.joining(",")));
                    dataset.setTotalRows(0);
                    datasetRepository.save(dataset);

                    // 批量读取并插入
                    List<DataRow> batch = new ArrayList<>(BATCH_SIZE);

                    while (rs.next()) {
                        Map<String, Object> rowData = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            rowData.put(columns.get(i - 1), rs.getObject(i));
                        }

                        DataRow dataRow = new DataRow();
                        dataRow.setDatasetId(datasetId);
                        dataRow.setRowIndex(++totalImported);
                        dataRow.setOriginalData(rowData);
                        batch.add(dataRow);

                        // 达到批次大小时插入
                        if (batch.size() >= BATCH_SIZE) {
                            saveBatch(batch);
                            batchCount++;
                            batch.clear();

                            // 更新进度
                            dataset.setProcessedRows(totalImported);
                            datasetRepository.save(dataset);

                            // 发布进度事件
                            publishProgress(datasetId, totalImported, -1);
                        }
                    }

                    // 插入剩余行
                    if (!batch.isEmpty()) {
                        saveBatch(batch);
                        batch.clear();
                    }

                    // 更新最终状态
                    dataset.setTotalRows(totalImported);
                    dataset.setProcessedRows(totalImported);
                    dataset.setStatus("uploaded");
                    datasetRepository.save(dataset);

                    config.setImportStatus("completed");
                    config.setLastImportTime(LocalDateTime.now());
                    syncConfigRepository.save(config);

                    log.info("数据导入完成: 数据源ID={}, 数据集ID={}, 总行数={}",
                        syncConfigId, datasetId, totalImported);

                }
            }

        } catch (Exception e) {
            log.error("数据导入失败", e);
            config.setImportStatus("failed");
            syncConfigRepository.save(config);
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveBatch(List<DataRow> batch) {
        dataRowRepository.saveAll(batch);
        dataRowRepository.flush();
    }

    private void publishProgress(Integer datasetId, int processed, int total) {
        // 通过 WebSocket 发布进度（现有机制）
        // ...
    }
}
```

**预估工作量**: 6小时

---

### 阶段 5：前端独立页面开发

#### 任务 5.1：创建数据源管理页面

**目标**: 创建独立的 CRUD 管理界面

**输入**: 后端 API

**输出**: Vue 组件

**涉及文件**:
- `frontend/src/views/DataSourcesView.vue` (新建)
- `frontend/src/router/index.ts` (新增路由)
- `frontend/src/App.vue` (新增导航菜单)

**路由配置**:

```typescript
// frontend/src/router/index.ts
{
  path: '/data-sources',
  component: () => import('../views/DataSourcesView.vue'),
  meta: { title: '数据源管理' }
}
```

**导航菜单**:

```vue
<!-- frontend/src/App.vue -->
<el-menu-item index="/data-sources">
  <el-icon><Database /></el-icon>
  <span>数据源管理</span>
</el-menu-item>
```

**页面实现**:

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DataSource } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'

const loading = ref(false)
const items = ref<DataSource[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<DataSource | null>(null)

// 表单数据
const form = ref({
  name: '',
  direction: 'import' as 'import' | 'export',
  dbType: 'mysql' as DbType,
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  tableName: '',
  importQuery: '',

  // Oracle 专用
  connectionMode: 'standard',
  oracleSid: '',
  oracleServiceName: '',
})

const testing = ref(false)

async function fetchList() {
  loading.value = true
  try {
    items.value = await dataSourcesApi.listDataSources()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  Object.assign(form.value, {
    name: '',
    direction: 'import',
    dbType: 'mysql',
    host: '',
    port: 3306,
    databaseName: '',
    username: '',
    password: '',
    tableName: '',
    importQuery: '',
    connectionMode: 'standard',
    oracleSid: '',
    oracleServiceName: '',
  })
  dialogVisible.value = true
}

function openEdit(row: DataSource) {
  dialogMode.value = 'edit'
  editing.value = row
  Object.assign(form.value, {
    name: row.name,
    direction: row.direction,
    dbType: row.dbType,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName,
    username: row.username,
    password: '',
    tableName: row.tableName,
    importQuery: row.importQuery || '',
    connectionMode: row.connectionMode || 'standard',
    oracleSid: row.oracleSid || '',
    oracleServiceName: row.oracleServiceName || '',
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    if (dialogMode.value === 'create') {
      await dataSourcesApi.createDataSource(form.value)
      ElMessage.success('创建成功')
    } else if (editing.value) {
      await dataSourcesApi.updateDataSource(editing.value.id, {
        ...form.value,
        password: form.value.password ? form.value.password : undefined,
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function onDelete(row: DataSource) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', {
      type: 'warning'
    })
    await dataSourcesApi.deleteDataSource(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

async function onTestConnection(row: DataSource) {
  testing.value = true
  try {
    const result = await dataSourcesApi.testConnection(row.id)
    if (result.success) {
      ElMessage.success('连接测试成功')
      row.connectionTestStatus = 'success'
      row.connectionTestTime = new Date().toISOString()
    } else {
      ElMessage.error(`连接测试失败: ${result.message}`)
      row.connectionTestStatus = 'failed'
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="data-sources-view">
    <div class="header">
      <h2>数据源管理</h2>
      <el-button type="primary" @click="openCreate">新建数据源</el-button>
    </div>

    <el-table :data="items" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="direction" label="方向" width="80">
        <template #default="{ row }">
          <el-tag :type="row.direction === 'import' ? 'success' : 'warning'" size="small">
            {{ row.direction === 'import' ? '导入' : '导出' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="dbType" label="类型" width="100" />
      <el-table-column prop="host" label="主机" min-width="140" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="tableName" label="表名" min-width="120" />
      <el-table-column prop="connectionTestStatus" label="连接状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.connectionTestStatus === 'success'" type="success" size="small">
            正常
          </el-tag>
          <el-tag v-else-if="row.connectionTestStatus === 'failed'" type="danger" size="small">
            失败
          </el-tag>
          <el-tag v-else type="info" size="small">未测试</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="onTestConnection(row)" :loading="testing">
            测试连接
          </el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建数据源' : '编辑数据源'"
      width="640px"
    >
      <el-form label-width="120px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="数据源配置名称" />
        </el-form-item>

        <el-form-item label="数据方向">
          <el-radio-group v-model="form.direction">
            <el-radio label="import">导入（从数据库读取）</el-radio>
            <el-radio label="export">导出（写入数据库）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="数据库类型">
          <el-select v-model="form.dbType" style="width: 100%">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="SQL Server" value="sqlserver" />
            <el-option label="Oracle" value="oracle" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址">
          <el-input v-model="form.host" placeholder="localhost 或 IP 地址" />
        </el-form-item>

        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>

        <!-- Oracle 连接模式配置 -->
        <template v-if="form.dbType === 'oracle'">
          <el-form-item label="连接模式">
            <el-select v-model="form.connectionMode" style="width: 100%">
              <el-option label="标准模式（自动选择）" value="standard" />
              <el-option label="SID 模式" value="sid" />
              <el-option label="Service Name 模式" value="service_name" />
              <el-option label="TNS 模式" value="tns" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.connectionMode === 'sid' || form.connectionMode === 'standard'"
                       label="Oracle SID">
            <el-input v-model="form.oracleSid" placeholder="例如: ORCL" />
          </el-form-item>

          <el-form-item v-if="form.connectionMode === 'service_name' || form.connectionMode === 'standard'"
                       label="Service Name">
            <el-input v-model="form.oracleServiceName" placeholder="例如: ORCLPDB" />
          </el-form-item>
        </template>

        <!-- 非 Oracle 数据库的数据库名字段 -->
        <el-form-item v-if="form.dbType !== 'oracle'" label="数据库名">
          <el-input v-model="form.databaseName" />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password
                   :placeholder="dialogMode === 'edit' ? '留空表示不更新' : ''" />
        </el-form-item>

        <el-form-item v-if="form.direction === 'import'" label="表名">
          <el-input v-model="form.tableName" placeholder="要导入的表名（支持 schema.table）" />
        </el-form-item>

        <el-form-item v-if="form.direction === 'import'" label="查询条件">
          <el-input
            v-model="form.importQuery"
            type="textarea"
            :rows="3"
            placeholder="可选的 WHERE 条件，例如：created_at > '2024-01-01' AND status = 'active'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.data-sources-view {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
```

**预估工作量**: 6小时

---

#### 任务 5.2：创建数据导入向导组件

**目标**: 向导式导入流程，支持预览、配置条件、确认导入

**输入**: 后端 API

**输出**: 向导组件

**涉及文件**:
- `frontend/src/components/DataImportWizard.vue` (新建)

**组件实现**:

```vue
<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { DataSource, TablePreview } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'

const emit = defineEmits(['import-completed'])

const visible = ref(false)
const currentStep = ref(0)
const loading = ref(false)

const selectedSourceId = ref<number>()
const selectedTableName = ref<string>('')

const preview = reactive<TablePreview>({
  tableName: '',
  columns: [],
  rows: [],
  totalRows: 0,
  sql: '',
})

const importQuery = ref('')

const datasetName = ref('')
const importMode = ref<'full' | 'incremental'>('full')

// 打开向导
function open() {
  visible.value = true
  currentStep.value = 0
  selectedSourceId.value = undefined
  selectedTableName.value = ''
  importQuery.value = ''
  datasetName.value = ''
  importMode.value = 'full'
}

// 选择数据源
function onSelectDataSource(sourceId: number) {
  selectedSourceId.value = sourceId
}

// 选择表
async function onSelectTable(tableName: string) {
  selectedTableName.value = tableName
  await loadPreview()
}

// 加载预览数据
async function loadPreview() {
  if (!selectedSourceId.value || !selectedTableName.value) return

  loading.value = true
  try {
    const result = await dataSourcesApi.previewTableData(
      selectedSourceId.value,
      selectedTableName.value,
      importQuery.value
    )
    Object.assign(preview, result)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载预览失败')
  } finally {
    loading.value = false
  }
}

// 刷新预览
async function onRefreshPreview() {
  await loadPreview()
}

// 确认导入
async function onConfirmImport() {
  if (!selectedSourceId.value || !datasetName.value) {
    ElMessage.warning('请完善导入配置')
    return
  }

  loading.value = true
  try {
    // 创建数据集
    const dataset = await dataSourcesApi.createDatasetFromSource({
      sourceId: selectedSourceId.value,
      tableName: selectedTableName.value,
      datasetName: datasetName.value,
      importQuery: importQuery.value,
      mode: importMode.value,
    })

    ElMessage.success('导入任务已创建，正在后台执行...')
    visible.value = false
    emit('import-completed', dataset)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建导入任务失败')
  } finally {
    loading.value = false
  }
}

// 计算属性
const steps = computed(() => [
  { title: '选择数据源', icon: 'Database' },
  { title: '选择表', icon: 'Grid' },
  { title: '配置导入', icon: 'Setting' },
  { title: '确认导入', icon: 'Check' },
])
</script>

<template>
  <el-dialog v-model="visible" title="从数据库导入数据" width="900px" :close-on-click-modal="false">
    <!-- 步骤指示器 -->
    <el-steps :active="currentStep" align-center style="margin-bottom: 24px">
      <el-step v-for="step in steps" :key="step.title" :title="step.title" />
    </el-steps>

    <!-- 步骤 1: 选择数据源 -->
    <div v-show="currentStep === 0" class="wizard-step">
      <h3>选择数据源</h3>
      <DataSourceSelector
        v-model="selectedSourceId"
        direction="import"
        @change="onSelectDataSource"
      />
    </div>

    <!-- 步骤 2: 选择表 -->
    <div v-show="currentStep === 1" class="wizard-step">
      <h3>选择表</h3>
      <TableBrowser
        v-if="selectedSourceId"
        :source-id="selectedSourceId"
        @select="onSelectTable"
      />
    </div>

    <!-- 步骤 3: 配置导入 -->
    <div v-show="currentStep === 2" class="wizard-step">
      <h3>配置导入</h3>

      <el-form label-width="120px">
        <el-form-item label="数据源">
          <el-tag>{{ selectedSourceId }}</el-tag>
        </el-form-item>

        <el-form-item label="表名">
          <el-tag>{{ selectedTableName }}</el-tag>
        </el-form-item>

        <el-form-item label="数据集名称">
          <el-input v-model="datasetName" placeholder="输入数据集名称" />
        </el-form-item>

        <el-form-item label="导入模式">
          <el-radio-group v-model="importMode">
            <el-radio label="full">全量导入</el-radio>
            <el-radio label="incremental">增量更新</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="查询条件">
          <el-input
            v-model="importQuery"
            type="textarea"
            :rows="3"
            placeholder="可选的 WHERE 条件，例如：created_at > '2024-01-01'"
            @blur="onRefreshPreview"
          />
        </el-form-item>
      </el-form>

      <div class="preview-section">
        <h4>数据预览（前 20 行）</h4>
        <el-table :data="preview.rows" v-loading="loading" border max-height="400">
          <el-table-column
            v-for="col in preview.columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
          />
        </el-table>
        <div class="preview-info">
          总行数: {{ preview.totalRows }}
        </div>
      </div>
    </div>

    <!-- 步骤 4: 确认导入 -->
    <div v-show="currentStep === 3" class="wizard-step">
      <h3>确认导入配置</h3>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="数据源ID">{{ selectedSourceId }}</el-descriptions-item>
        <el-descriptions-item label="表名">{{ selectedTableName }}</el-descriptions-item>
        <el-descriptions-item label="数据集名称">{{ datasetName }}</el-descriptions-item>
        <el-descriptions-item label="导入模式">
          {{ importMode === 'full' ? '全量导入' : '增量更新' }}
        </el-descriptions-item>
        <el-descriptions-item label="查询条件" :span="2">
          {{ importQuery || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="预计导入行数" :span="2">
          {{ preview.totalRows }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        type="info"
        :closable="false"
        style="margin-top: 16px"
      >
        确认后将创建导入任务，数据将在后台异步导入。您可以在数据集列表中查看导入进度。
      </el-alert>
    </div>

    <!-- 底部按钮 -->
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
      <el-button
        v-if="currentStep < 3"
        type="primary"
        :disabled="!canProceed"
        @click="currentStep++"
      >
        下一步
      </el-button>
      <el-button
        v-if="currentStep === 3"
        type="primary"
        :loading="loading"
        @click="onConfirmImport"
      >
        确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.wizard-step {
  min-height: 400px;
}

.preview-section {
  margin-top: 24px;
}

.preview-section h4 {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
}

.preview-info {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}
</style>
```

**预估工作量**: 8小时

---

### 阶段 6：增量更新功能实现

#### 任务 6.1：实现增量更新逻辑

**目标**: 基于时间戳实现增量数据更新

**输入**: 数据源 ID, 上次导入时间, 更新模式

**输出**: 增量更新结果

**涉及文件**:
- `backend/src/main/java/com/datalabeling/service/DataImportService.java` (扩展)

**实现方案**:

```java
/**
 * 增量更新数据
 *
 * @param syncConfigId 数据源配置ID
 * @param datasetId    数据集ID
 * @param mode         更新模式：append=追加, replace=覆盖
 */
public void incrementUpdate(Integer syncConfigId, Integer datasetId,
                             String mode) {

    SyncConfig config = syncConfigRepository.findById(syncConfigId)
        .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在"));

    Dataset dataset = datasetRepository.findById(datasetId)
        .orElseThrow(() -> new IllegalArgumentException("数据集不存在"));

    // 检查是否支持增量更新
    if (config.getLastImportTime() == null) {
        throw new IllegalStateException("该数据源未进行过全量导入，无法增量更新");
    }

    // 构建 WHERE 条件（基于时间戳）
    String whereClause = buildIncrementalWhereClause(config, dataset);

    if ("replace".equals(mode)) {
        // 覆盖模式：清空后重新导入
        dataRowRepository.deleteByDatasetId(datasetId);
        dataset.setTotalRows(0);
        dataset.setProcessedRows(0);
    }

    // 执行导入（复用现有逻辑）
    importData(syncConfigId, datasetId, whereClause);
}

/**
 * 构建增量更新的 WHERE 条件
 */
private String buildIncrementalWhereClause(SyncConfig config, Dataset dataset) {
    LocalDateTime lastImportTime = config.getLastImportTime();

    // 尝试使用常见的时间戳字段
    List<String> timeFields = Arrays.asList(
        "updated_at", "update_time", "last_updated",
        "modified_at", "modify_time"
    );

    // 如果配置中指定了查询条件，先检查是否包含时间字段
    String baseCondition = config.getImportQuery();

    // 构建时间戳条件
    String timeCondition = null;

    // 这里可以查询表结构，自动检测时间戳字段
    // MVP 阶段简化处理：让用户在 importQuery 中指定

    if (StringUtils.isNotBlank(baseCondition)) {
        // 用户提供了自定义条件
        if (baseCondition.toLowerCase().contains("updated_at") ||
            baseCondition.toLowerCase().contains("update_time")) {
            timeCondition = baseCondition;
        }
    }

    // 如果没有找到时间字段，使用默认策略
    if (timeCondition == null) {
        // 假设表中有 updated_at 字段
        timeCondition = String.format("updated_at > '%s'",
            lastImportTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    return timeCondition;
}
```

**预估工作量**: 4小时

---

#### 任务 6.2：扩展前端支持增量更新

**目标**: 在数据集详情页添加"增量更新"按钮

**输入**: Dataset ID

**输出**: 增量更新操作

**涉及文件**:
- `frontend/src/views/DatasetDetailView.vue` (修改)

**UI 实现**:

```vue
<template>
  <div class="dataset-detail">
    <!-- 现有内容 -->

    <!-- 新增：如果是数据库来源，显示增量更新选项 -->
    <div v-if="dataset.sourceType === 'database'" class="database-actions">
      <el-button type="primary" @click="openIncrementalUpdate">
        增量更新
      </el-button>
      <el-button @click="openReimport">
        重新导入
      </el-button>
    </div>

    <!-- 增量更新对话框 -->
    <el-dialog v-model="incrementalDialogVisible" title="增量更新" width="600px">
      <el-form label-width="120px">
        <el-form-item label="上次导入时间">
          <el-tag>{{ lastImportTime }}</el-tag>
        </el-form-item>

        <el-form-item label="更新模式">
          <el-radio-group v-model="updateMode">
            <el-radio label="append">
              追加模式：保留已有数据，追加新数据
            </el-radio>
            <el-radio label="replace">
              覆盖模式：清空后重新导入全部数据
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="时间字段">
          <el-input v-model="timeField" placeholder="例如：updated_at（留空自动检测）" />
        </el-form-item>
      </el-form>

      <el-alert type="warning" :closable="false" style="margin-bottom: 16px">
        追加模式会增加数据行数；覆盖模式会清空已有数据，请谨慎操作。
      </el-alert>

      <template #footer>
        <el-button @click="incrementalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="onConfirmIncrementalUpdate">
          确认更新
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
```

**预估工作量**: 3小时

---

### 阶段 7：测试与优化

#### 任务 7.1：单元测试

**目标**: 编写核心业务逻辑的单元测试

**涉及文件**:
- `backend/src/test/java/com/datalabeling/service/DataImportServiceTest.java`
- `backend/src/test/java/com/datalabeling/util/OracleConnectionUrlBuilderTest.java`

**测试用例**:
- Oracle URL 构建测试（各种连接模式）
- 数据导入分批处理测试
- 增量更新逻辑测试

**预估工作量**: 4小时

---

#### 任务 7.2：集成测试

**目标**: 测试完整的数据源配置和导入流程

**测试场景**:
1. 创建 MySQL 数据源 → 测试连接 → 浏览表 → 预览数据 → 导入数据
2. 创建 Oracle 数据源（SID 模式）→ 测试连接 → 导入数据
3. 创建 Oracle 数据源（Service Name 模式）→ 测试连接 → 导入数据
4. 增量更新测试

**预估工作量**: 5小时

---

#### 任务 7.3：性能测试和优化

**目标**: 测试大表导入性能，优化内存和速度

**测试数据**:
- 10万行数据表
- 100万行数据表（如有可能）

**优化点**:
- JDBC fetchSize 调优
- 批次大小调整
- 连接池配置优化

**预估工作量**: 6小时

---

#### 任务 7.4：安全性检查

**目标**: SQL 注入测试、密码加密验证、权限隔离验证

**检查清单**:
- [ ] WHERE 条件参数化查询
- [ ] 密码加密存储验证
- [ ] 跨用户访问隔离测试
- [ ] 数据源连接泄露测试

**预估工作量**: 3小时

---

## 风险评估与解决方案

### 风险 1：Oracle 驱动版本兼容性
- **风险等级**: 中
- **解决方案**: 提供多个版本的 Oracle 驱动（ojdbc8/10/11），根据目标数据库版本自动选择
- **缓解措施**: 文档中说明各驱动适用的 Oracle 版本范围

### 风险 2：大数据量导入内存溢出
- **风险等级**: 高
- **解决方案**:
  - 使用流式处理（ResultSet.setFetchSize）
  - 分批提交（每批1000行）
  - 限制单次导入行数上限（可配置）
- **缓解措施**: 提供导入进度监控，支持取消任务

### 风险 3：SQL 注入攻击
- **风险等级**: 高
- **解决方案**:
  - 严格使用 PreparedStatement
  - WHERE 条件构建器仅支持简单表达式
  - 禁止用户输入完整 SQL 语句
- **缓解措施**: 安全测试用例覆盖

### 风险 4：外部数据源连接泄露
- **风险等级**: 中
- **解决方案**:
  - 使用连接池（Druid）
  - try-with-resources 自动释放连接
  - 定时清理空闲连接
- **缓解措施**: 监控连接池状态

### 风险 5：sync_configs 表语义混淆
- **风险等级**: 低
- **解决方案**:
  - 通过 `direction` 字段明确区分导入/导出
  - 前端界面根据方向显示不同字段
  - API 层添加参数校验
- **缓解措施**: 清晰的文档和代码注释

### 风险 6：增量更新时间字段识别
- **风险等级**: 中
- **解决方案**:
  - MVP 阶段：让用户在 importQuery 中指定时间字段
  - 后续优化：自动查询表结构，检测时间戳字段
- **缓解措施**: 提供常用时间字段列表（updated_at, update_time 等）

### 风险 7：增量更新覆盖模式丢失标注结果
- **风险等级**: 中
- **解决方案**:
  - 覆盖前弹窗二次确认
  - 提供"备份"选项（导出当前标注结果）
  - 日志记录覆盖操作
- **缓解措施**: 明确提示用户数据会丢失

---

## 依赖项

### 后端新增依赖

```xml
<!-- Oracle JDBC Drivers -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>19.8.0.0</version>
    <optional>true</optional>
</dependency>

<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc10</artifactId>
    <version>19.8.0.0</version>
    <optional>true</optional>
</dependency>

<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>21.3.0.0</version>
    <optional>true</optional>
</dependency>
```

### 数据库迁移脚本

1. `backend/src/main/resources/db/migration/VXX__alter_sync_configs_for_import.sql`
2. `backend/src/main/resources/db/migration/VXX__alter_datasets_for_source_tracking.sql`

---

## 数据流图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端界面                               │
├─────────────────────────────────────────────────────────────┤
│  DataSourcesView.vue  │  DataImportWizard.vue  │  DatasetDetailView.vue  │
│  - CRUD 数据源         │  - 选择数据源和表       │  - 增量更新按钮           │
│  - 测试连接            │  - 预览数据             │  - 显示数据来源           │
│  - 浏览数据库/表       │  - 配置导入条件         │                          │
└──────────────┬────────────────────┬──────────────────────────┘
               │                    │
               ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                      后端 API 层                              │
├─────────────────────────────────────────────────────────────┤
│  ExternalDataSourceController                               │
│  - POST   /api/data-sources               创建数据源         │
│  - GET    /api/data-sources               列表查询           │
│  - GET    /api/data-sources/{id}/test     测试连接           │
│  - GET    /api/data-sources/{id}/explore  浏览数据库/表      │
│  - GET    /api/data-sources/{id}/preview  预览表数据         │
│  - POST   /api/data-sources/{id}/import   创建导入任务       │
│  - POST   /api/datasets/{id}/incremental  增量更新           │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│                      服务层                                  │
├─────────────────────────────────────────────────────────────┤
│  ExternalDataSourceService     │  DataImportService         │
│  - testConnection()            │  - importData()            │
│  - exploreDatabases()          │  - incrementUpdate()       │
│  - previewTableData()          │  - saveBatch()             │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  数据源管理层                                 │
├─────────────────────────────────────────────────────────────┤
│  ExternalDataSourceManager                                  │
│  - getDataSource(id)       获取或创建数据源                  │
│  - createDataSource()      创建 Druid 连接池                 │
│  - evictDataSource()       清理缓存                          │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│                  动态数据源路由                               │
├─────────────────────────────────────────────────────────────┤
│  DynamicDataSource extends AbstractRoutingDataSource        │
│  - determineCurrentLookupKey()   路由到指定数据源            │
└──────────────┬──────────────────────────────────────────────┘
               │
      ┌────────┴────────┐
      │                 │
      ▼                 ▼
┌───────────┐    ┌─────────────┐
│ 主数据源   │    │  外部数据源   │
│ (MySQL)    │    │  (动态创建)   │
│            │    │  - MySQL     │
│ DataLabel  │    │  - Oracle    │
│ 数据库      │    │  - PG        │
└───────────┘    │  - SQLServer │
                 └─────────────┘
```

---

## API 接口设计

### 1. 数据源管理 API

#### 1.1 创建数据源

**请求**:
```http
POST /api/data-sources
Content-Type: application/json

{
  "name": "生产环境 MySQL",
  "direction": "import",
  "dbType": "mysql",
  "host": "192.168.1.100",
  "port": 3306,
  "databaseName": "production_db",
  "username": "readonly_user",
  "password": "plaintext_password",
  "tableName": "users"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 1,
    "name": "生产环境 MySQL",
    "direction": "import",
    "dbType": "mysql",
    "host": "192.168.1.100",
    "port": 3306,
    "databaseName": "production_db",
    "username": "readonly_user",
    "tableName": "users",
    "connectionTestStatus": "unknown",
    "createdAt": "2024-01-03T10:00:00"
  }
}
```

#### 1.2 测试连接

**请求**:
```http
POST /api/data-sources/{id}/test
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "success": true,
    "message": "连接成功",
    "latency": 45
  }
}
```

#### 1.3 浏览数据库和表

**请求**:
```http
GET /api/data-sources/{id}/explore
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "databases": [
      {
        "name": "production_db",
        "tables": [
          {
            "name": "users",
            "type": "TABLE",
            "rowCount": 150000
          },
          {
            "name": "orders",
            "type": "TABLE",
            "rowCount": 500000
          }
        ]
      }
    ]
  }
}
```

#### 1.4 预览表数据

**请求**:
```http
GET /api/data-sources/{id}/preview?tableName=users&where=status='active'
```

**响应**:
```json
{
  "code": 200,
  "data": {
    "tableName": "users",
    "columns": ["id", "name", "email", "status", "created_at"],
    "rows": [
      {
        "id": 1,
        "name": "张三",
        "email": "zhangsan@example.com",
        "status": "active",
        "created_at": "2024-01-01T10:00:00"
      },
      // ... 更多行（最多20行）
    ],
    "totalRows": 150000,
    "sql": "SELECT * FROM users WHERE status='active' LIMIT 20"
  }
}
```

#### 1.5 创建导入任务

**请求**:
```http
POST /api/data-sources/{id}/import
Content-Type: application/json

{
  "tableName": "users",
  "datasetName": "用户数据集",
  "importQuery": "status='active' AND created_at > '2024-01-01'",
  "mode": "full"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "导入任务已创建",
  "data": {
    "datasetId": 123,
    "taskId": "import-task-456",
    "estimatedRows": 50000,
    "status": "importing"
  }
}
```

#### 1.6 增量更新

**请求**:
```http
POST /api/datasets/{id}/incremental
Content-Type: application/json

{
  "sourceId": 1,
  "mode": "append",
  "timeField": "updated_at"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "增量更新任务已创建",
  "data": {
    "taskId": "incremental-task-789",
    "lastImportTime": "2024-01-02T10:00:00",
    "status": "importing"
  }
}
```

---

## 前端 API 封装

创建 `frontend/src/api/dataSources.ts`:

```typescript
export interface DataSource {
  id: number
  name: string
  direction: 'import' | 'export'
  dbType: DbType
  host: string
  port: number
  databaseName?: string
  username: string
  tableName?: string
  importQuery?: string

  // Oracle 专用
  connectionMode?: string
  oracleSid?: string
  oracleServiceName?: string

  // 状态
  connectionTestStatus?: string
  connectionTestTime?: string
  importStatus?: string
  lastImportTime?: string

  createdAt: string
  updatedAt: string
}

export type DbType = 'mysql' | 'postgresql' | 'sqlserver' | 'oracle'

export interface TablePreview {
  tableName: string
  columns: string[]
  rows: Record<string, any>[]
  totalRows: number
  sql: string
}

export interface ImportRequest {
  sourceId: number
  tableName: string
  datasetName: string
  importQuery?: string
  mode: 'full' | 'incremental'
}

// API 方法
export function listDataSources(): Promise<DataSource[]>
export function createDataSource(data: Partial<DataSource>): Promise<DataSource>
export function updateDataSource(id: number, data: Partial<DataSource>): Promise<DataSource>
export function deleteDataSource(id: number): Promise<void>
export function testConnection(id: number): Promise<{ success: boolean; message: string; latency: number }>
export function exploreDatabases(id: number): Promise<any>
export function previewTableData(id: number, tableName: string, whereClause?: string): Promise<TablePreview>
export function createDatasetFromSource(req: ImportRequest): Promise<any>
export function incrementalUpdate(datasetId: number, req: any): Promise<any>
```

---

## 用户反馈区域

```
请在此区域补充您对整体规划的意见和建议：

用户补充内容：

---

---

---
```

---

## 总结

本规划文档基于以下用户决策生成：

1. ✅ **复用 sync_configs 表** - 通过 `direction` 字段区分导入/导出
2. ✅ **分批全量导入** - 每批1000行，使用流式处理
3. ✅ **Oracle 多模式支持** - SID/ServiceName/TNS，包含多个驱动版本
4. ✅ **增量更新支持** - 基于时间戳的增量查询
5. ✅ **独立前端页面** - `/data-sources` 路由，独立导航菜单

### 主要改动点

- **数据库表**: sync_configs 新增 10 个字段（direction, Oracle 字段, 导入字段等）
- **实体类**: SyncConfig 新增对应属性，调整验证规则
- **依赖**: 新增 3 个 Oracle JDBC 驱动（ojdbc8/10/11）
- **前端**: 新增独立页面 DataSourcesView.vue + 数据导入向导组件
- **后端**: 新增动态数据源管理器、Oracle URL 构建器、增量更新逻辑

### 预计总工作量

- **后端开发**: 10-12 天
- **前端开发**: 5-6 天
- **测试与优化**: 3-4 天
- **总计**: **约 3-4 周**

如有任何疑问或需要调整的地方，请随时告知！
