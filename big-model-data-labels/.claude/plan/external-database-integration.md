# 外部数据源集成方案设计与开发计划

## 项目概述

**功能名称**: 外部数据库（MySQL/Oracle）数据源集成

**项目目标**: 扩展数据标注平台，支持从外部 MySQL/Oracle 数据库直接导入数据创建数据集，与现有文件上传方式保持统一的用户体验，实现数据源的安全管理和高效数据导入。

---

## 一、需求背景

当前数据标注平台仅支持通过 Excel/CSV 文件上传方式创建数据集。现在需要扩展功能，支持直接对接外部 MySQL 和 Oracle 数据库，将其表数据作为数据集进行数据分析和标签提取。

### 现有技术栈
- 后端: Java 8 + Spring Boot 2.7 + Maven + JPA
- 前端: Vue 3 + Element Plus
- 数据库: MySQL 8.0+ (当前主数据库)
- 连接池: Druid
- 其他依赖: 已包含 mysql-connector-java, mssql-jdbc, postgresql

### 现有数据集模型
已有 Dataset 实体，包含字段：
- id, userId, name, originalFilename
- columns (列信息JSON)
- totalRows, status (uploaded/archived)
- description

### 功能需求
1. 支持在线配置外部 MySQL/Oracle 数据源连接
2. 支持测试连接有效性
3. 支持浏览数据源中的数据库/表列表
4. 支持选择表预览数据
5. 支持将表数据导入为数据集
6. 支持配置 SQL 查询条件（可选）
7. 数据源配置需加密存储密码
8. 数据源配置按用户隔离

---

## 二、待明确问题

### 问题 1: 表结构设计方案

现有 `sync_configs` 表设计用于"数据同步到外部数据库"场景。新需求是"从外部数据库导入数据"。

**方案 A: 复用 sync_configs 表，扩展用途**
- 优点: 减少表数量，配置统一管理，复用现有加密机制
- 缺点: 表语义不清晰，字段混杂
- 改造: 新增 `direction` 字段(import/export)，部分条件可选

**方案 B: 新建 external_data_sources 表**
- 优点: 职责清晰，表结构专门优化，独立演化
- 缺点: 增加维护成本
- 设计: 简化结构，只保留连接信息

**等待用户选择**: 请明确使用方案 A 或方案 B

---

### 问题 2: 大数据量导入性能策略

**方案 A: 一次性全量导入 + 分批提交**
- 策略: 每次从源库读取 1000 行，插入一批后提交事务
- 优点: 实现简单，适合中小表(<100万行)
- 缺点: 大表可能内存溢出，中断后需从头开始

**方案 B: 流式导入 + 断点续传**
- 策略: 使用游标流式读取 + 批量写入 + 记录已导入的主键范围
- 优点: 内存占用低，支持中断续传，适合大表
- 缺点: 实现复杂

**等待用户选择**: 请明确使用方案 A 或方案 B

---

### 问题 3: Oracle 连接配置方式

Oracle JDBC 连接字符串格式复杂，支持 SID、ServiceName、TNS 等多种方式。

**方案 A: 简化配置，仅支持 ServiceName**
- 配置项: host, port, service_name
- JDBC URL: `jdbc:oracle:thin:@//host:port/service_name`
- 优点: 配置简单，覆盖 90% 场景
- 缺点: 无法支持老式 SID 连接

**方案 B: 完整配置，支持多种模式**
- 配置项: host, port, service_name/sid(可选), tns_name(可选), connection_mode
- 优点: 覆盖所有场景
- 缺点: 配置复杂

**等待用户选择**: 请明确使用方案 A 或方案 B

---

### 问题 4: 导入数据更新策略

当外部数据源的数据发生变化时，如何处理已导入的数据集?

**方案 A: 静态快照，不支持更新**
- 策略: 导入时复制数据，与外部源完全解耦
- 优点: 简单，数据一致性好，性能高
- 缺点: 数据可能过时

**方案 B: 支持增量更新**
- 策略: 提供"重新导入"功能，覆盖或追加数据
- 优点: 数据可保持最新
- 缺点: 可能丢失标注结果，实现复杂

**等待用户选择**: 请明确使用方案 A 或方案 B

---

### 问题 5: 前端 UI 风格和交互方式

**方案 A: 独立页面 + 导航菜单项**
- 入口: 侧边栏新增"数据源管理"菜单项
- 布局: 独立页面，列表+表单弹窗
- 优点: 功能独立，不干扰现有流程

**方案 B: 集成到数据集页面**
- 入口: 在"上传数据集"按钮旁增加"从数据库导入"按钮
- 布局: 数据集页面的 Tab 或 Modal
- 优点: 流程连贯，UI 集中

**等待用户选择**: 请明确使用方案 A 或方案 B

---

## 三、技术架构设计

### 1. 数据库表设计

#### 方案 B: external_data_sources 表结构

```sql
CREATE TABLE external_data_sources (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL COMMENT '用户ID',
    name VARCHAR(100) NOT NULL COMMENT '数据源名称',
    db_type VARCHAR(20) NOT NULL COMMENT '数据库类型:mysql/oracle/postgresql/sqlserver',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    database_name VARCHAR(100) COMMENT '数据库名(MySQL/PostgreSQL)',
    service_name VARCHAR(100) COMMENT 'Oracle服务名',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    password_encrypted TEXT NOT NULL COMMENT '加密后的密码',
    properties JSON COMMENT '额外连接参数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    last_tested_at TIMESTAMP NULL COMMENT '最后测试连接时间',
    test_status VARCHAR(20) COMMENT '测试状态:success/failed',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_db_type (db_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部数据源配置';
```

#### Dataset 实体扩展

```sql
ALTER TABLE datasets
ADD COLUMN source_type VARCHAR(20) DEFAULT 'file' COMMENT '数据来源:file/database',
ADD COLUMN external_source_id INT COMMENT '外部数据源ID(当source_type=database时)',
ADD COLUMN import_query TEXT COMMENT '导入时使用的SQL查询条件',
ADD INDEX idx_source_type (source_type),
ADD INDEX idx_external_source_id (external_source_id);
```

---

### 2. 动态数据源架构

```java
// 数据源上下文持有者
public class DataSourceContextHolder {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

    public static void setDataSourceKey(String key) {
        contextHolder.set(key);
    }

    public static String getDataSourceKey() {
        return contextHolder.get();
    }

    public static void clearDataSourceKey() {
        contextHolder.remove();
    }
}

// 动态数据源
public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceContextHolder.getDataSourceKey();
    }
}
```

---

### 3. 数据导入流程

```
1. 用户选择数据源 → 测试连接
2. 浏览数据库/表 → 选择目标表
3. 预览数据(前20行) → 确认列信息
4. 配置 WHERE 条件(可选) → 预览过滤结果
5. 确认导入 → 创建 Dataset 记录(source_type='database')
6. 异步导入任务:
   - 使用动态数据源连接外部库
   - 流式读取数据(JDBC ResultSet + fetch_size)
   - 每1000行一批插入 data_rows 表
   - 更新导入进度
   - 完成后标记 Dataset 状态为 'uploaded'
```

---

### 4. 安全方案

- **密码加密**: 复用现有 `SyncCryptoService`，AES-256-CBC 加密
- **SQL 注入防护**:
  - 使用 PreparedStatement 参数化查询
  - WHERE 条件构建器仅支持简单表达式(列名 操作符 值)
  - 禁止用户输入完整 SQL 语句
- **权限隔离**:
  - 所有 API 强制检查 userId
  - 数据源查询自动添加 `WHERE user_id = ?`
  - 禁止跨用户访问

---

### 5. 性能优化

- **连接池管理**:
  - 外部数据源使用独立连接池(HikariCP)
  - 限制最大连接数(每数据源 ≤ 5)
  - 空闲超时自动释放连接

- **大表导入优化**:
  - JDBC ResultSet 设置 `fetchSize=1000` (流式读取)
  - 禁用自动提交，每批1000行手动提交
  - 使用 `INSERT VALUES (...),(...),(...)` 批量插入
  - 异步任务执行，避免阻塞主线程

- **内存优化**:
  - 不缓存全表数据，流式处理
  - 及时释放 JDBC 资源(Connection/Statement/ResultSet)
  - 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 控制事务边界

---

## 四、详细任务分解

### 阶段 1: 数据库设计与核心实体 (预计 1-2 天)

#### 任务 1.1: 设计外部数据源配置表结构
- 目标: 创建存储外部数据源配置的数据库表
- 输入: 现有 sync_configs 表结构，业务需求
- 输出: SQL 建表语句，索引设计
- 涉及文件:
  - `backend/src/main/resources/db/migration/VXX__create_external_data_sources.sql`
- 预估工作量: 2-3 小时

#### 任务 1.2: 创建 ExternalDataSource 实体类
- 目标: 映射外部数据源配置表
- 输入: 数据库表设计
- 输出: JPA 实体类
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/entity/ExternalDataSource.java`
- 预估工作量: 1 小时

#### 任务 1.3: 扩展 Dataset 实体支持多种来源
- 目标: 为 Dataset 实体添加数据源类型和关联字段
- 输入: 现有 Dataset 实体
- 输出: 修改后的 Dataset 实体
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/entity/Dataset.java`
  - 数据库迁移脚本(新增 source_type, external_source_id, import_query 字段)
- 预估工作量: 1-2 小时

#### 任务 1.4: 创建 Repository 层
- 目标: 创建数据访问层接口
- 输入: 实体类
- 输出: Repository 接口
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/repository/ExternalDataSourceRepository.java`
- 预估工作量: 0.5 小时

---

### 阶段 2: 动态数据源架构 (预计 2-3 天)

#### 任务 2.1: 添加 Oracle JDBC 驱动依赖
- 目标: 在 pom.xml 中添加 Oracle 驱动
- 输入: Oracle 版本要求
- 输出: 更新后的 pom.xml
- 涉及文件:
  - `backend/pom.xml`
- 预估工作量: 0.5 小时

#### 任务 2.2: 实现动态数据源路由器
- 目标: 创建基于 ThreadLocal 的数据源路由器
- 输入: Spring 动态数据源最佳实践
- 输出: DynamicDataSource 类
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/config/DynamicDataSource.java`
  - `backend/src/main/java/com/datalabeling/config/DataSourceContextHolder.java`
- 预估工作量: 2-3 小时

#### 任务 2.3: 配置多数据源 Bean
- 目标: 创建主数据源和动态数据源配置类
- 输入: 数据库连接配置
- 输出: DataSourceConfig 配置类
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/config/DataSourceConfig.java`
  - `backend/src/main/resources/application.yml` (新增外部数据源配置)
- 预估工作量: 2-3 小时

#### 任务 2.4: 实现数据源连接池管理
- 目标: 管理外部数据源的连接池创建、销毁和缓存
- 输入: 数据源配置信息
- 输出: ExternalDataSourceManager 服务类
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDataSourceManager.java`
- 预估工作量: 3-4 小时

#### 任务 2.5: 实现数据源连接测试
- 目标: 提供测试外部数据源连接有效性的方法
- 输入: 数据源配置
- 输出: 连接测试方法
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDataSourceService.java` (testConnection 方法)
- 预估工作量: 1-2 小时

---

### 阶段 3: 数据导入服务 (预计 3-4 天)

#### 任务 3.1: 实现数据库/表列表浏览
- 目标: 获取数据源中的数据库和表列表
- 输入: 数据源 ID
- 输出: 数据库和表树形结构
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDataSourceService.java` (listDatabases, listTables 方法)
  - `backend/src/main/java/com/datalabeling/dto/response/DatabaseExplorerVO.java`
- 预估工作量: 3-4 小时

#### 任务 3.2: 实现表数据预览
- 目标: 支持预览表的前 N 行数据(类似文件上传的预览)
- 输入: 数据源 ID, 表名, WHERE 条件
- 输出: 预览数据 JSON
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDataSourceService.java` (previewTableData 方法)
  - `backend/src/main/java/com/datalabeling/dto/response/TablePreviewVO.java`
- 预估工作量: 2-3 小时

#### 任务 3.3: 实现大数据量分批导入
- 目标: 使用流式处理和分批插入导入大数据表
- 输入: 数据源 ID, SQL 查询, 目标 Dataset ID
- 输出: 导入的行数和进度
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDataImportService.java`
  - `backend/src/main/java/com/datalabeling/config/AsyncConfig.java` (导入任务线程池)
- 预估工作量: 4-6 小时

#### 任务 3.4: 实现 SQL 查询条件构建
- 目标: 支持用户配置 WHERE 条件过滤数据
- 输入: 用户输入的查询条件
- 输出: 安全的 SQL 语句(防注入)
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/SqlQueryBuilder.java`
- 预估工作量: 2-3 小时

#### 任务 3.5: 扩展 ExternalDbService 支持 Oracle
- 目标: 修改现有服务支持 Oracle 数据库
- 输入: Oracle JDBC 特性
- 输出: 更新后的 ExternalDbService
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/service/ExternalDbService.java`
- 预估工作量: 2-3 小时

#### 任务 3.6: 创建数据导入 API
- 目标: 提供数据导入的 REST API
- 输入: 导入请求参数
- 输出: API 端点
- 涉及文件:
  - `backend/src/main/java/com/datalabeling/controller/ExternalDataSourceController.java`
- 预估工作量: 2 小时

---

### 阶段 4: 前端界面开发 (预计 3-4 天)

#### 任务 4.1: 创建数据源管理页面
- 目标: 创建数据源 CRUD 管理界面
- 输入: 后端 API
- 输出: Vue 组件
- 涉及文件:
  - `frontend/src/views/ExternalDataSourcesView.vue`
- 预估工作量: 4-6 小时

#### 任务 4.2: 创建数据源配置表单组件
- 目标: 支持动态表单，根据数据库类型显示不同配置项
- 输入: 数据库类型配置
- 输出: 表单组件
- 涉及文件:
  - `frontend/src/components/DataSourceForm.vue`
- 预估工作量: 3-4 小时

#### 任务 4.3: 创建数据库浏览器组件
- 目标: 树形展示数据库和表，支持搜索和筛选
- 输入: 后端 API
- 输出: 树形组件
- 涉及文件:
  - `frontend/src/components/DatabaseExplorer.vue`
- 预估工作量: 4-5 小时

#### 任务 4.4: 创建数据预览和导入向导
- 目标: 向导式导入流程，支持预览、配置条件、确认导入
- 输入: 后端 API
- 输出: 向导组件
- 涉及文件:
  - `frontend/src/components/DataImportWizard.vue`
- 预估工作量: 5-6 小时

#### 任务 4.5: 扩展数据集列表页面
- 目标: 在数据集列表中支持显示来源类型和创建方式
- 输入: Dataset 实体新字段
- 输出: 更新后的 DatasetsView.vue
- 涉及文件:
  - `frontend/src/views/DatasetsView.vue`
- 预估工作量: 2-3 小时

#### 任务 4.6: 创建 API 调用封装
- 目标: 封装前端 API 调用方法
- 输入: 后端 API 规范
- 输出: API 方法
- 涉及文件:
  - `frontend/src/api/externalDataSources.ts`
- 预估工作量: 1-2 小时

---

### 阶段 5: 测试与优化 (预计 2-3 天)

#### 任务 5.1: 单元测试
- 目标: 编写核心业务逻辑的单元测试
- 输入: 业务代码
- 输出: 测试用例
- 涉及文件:
  - `backend/src/test/java/com/datalabeling/service/ExternalDataSourceServiceTest.java`
- 预估工作量: 3-4 小时

#### 任务 5.2: 集成测试
- 目标: 测试完整的数据源配置和导入流程
- 输入: 测试环境
- 输出: 测试报告
- 涉及文件: 无
- 预估工作量: 4-5 小时

#### 任务 5.3: 性能测试和优化
- 目标: 测试大表导入性能，优化内存和速度
- 输入: 大数据量测试表
- 输出: 性能优化方案
- 涉及文件:
  - 优化后的 `ExternalDataImportService.java`
- 预估工作量: 4-6 小时

#### 任务 5.4: 安全性检查
- 目标: SQL 注入测试、密码加密验证、权限隔离验证
- 输入: 安全测试清单
- 输出: 安全加固方案
- 涉及文件: 根据检查结果修改
- 预估工作量: 2-3 小时

#### 任务 5.5: 异常处理和错误提示优化
- 目标: 完善各种异常场景的错误提示
- 输入: 用户反馈
- 输出: 优化后的异常处理
- 涉及文件: 全局异常处理器 + 前端错误提示组件
- 预估工作量: 2-3 小时

---

## 五、风险评估与解决方案

### 风险 1: 大数据量导入内存溢出
- **风险等级**: 高
- **解决方案**: 使用流式处理 + 分批提交 + 限制单次导入行数上限

### 风险 2: SQL 注入攻击
- **风险等级**: 高
- **解决方案**: 严格使用 PreparedStatement + WHERE 条件构建器白名单

### 风险 3: 外部数据源连接泄露
- **风险等级**: 中
- **解决方案**: 连接池配置 + try-with-resources + 定时清理空闲连接

### 风险 4: Oracle 驱动许可证问题
- **风险等级**: 低
- **解决方案**: 使用 ojdbc8 (GPLv2 + Classpath Exception)

### 风险 5: 动态数据源事务管理复杂
- **风险等级**: 中
- **解决方案**: 明确事务边界，外部数据源操作使用独立事务

---

## 六、依赖项

### 后端新增依赖
```xml
<!-- Oracle JDBC Driver -->
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc8</artifactId>
    <version>19.8.0.0</version>
</dependency>

<!-- HikariCP (如需更好的连接池性能) -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

---

## 七、用户反馈区域

```
请在此区域补充您对整体规划的意见和建议:

[用户填写内容]

---
```

---

## 八、下一步行动

1. **请您先回答上述 5 个待明确的问题**
2. 我将根据您的选择更新规划文档
3. 确认后即可开始按阶段执行开发任务

如有任何疑问或需要调整的地方，请随时告诉我！
