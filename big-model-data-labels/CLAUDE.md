# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**智能数据标注与分析平台** - 基于DeepSeek大模型的自动化数据标注系统

本平台允许用户通过自定义标签规则，利用DeepSeek大模型对 Excel/CSV 文件进行自动批量标注、人工修正和分析。核心特性包括：
- 多用户隔离的数据处理工作流
- 灵活的自定义标签系统（支持版本管理）
- 实时进度跟踪的批量数据分析
- 结果归档、导出和数据库同步
- 标签命中统计和关键词分析

## 核心架构设计原则

### 技术栈要求
- **后端**: 应支持异步任务处理（大模型调用耗时较长）
- **前端**: 需要实时进度更新能力（WebSocket 或 Server-Sent Events）
- **存储**: 需要处理大文件和历史版本数据
- **大模型集成**: 使用DeepSeek API（OpenAI兼容接口），需要配置API密钥

### 数据隔离策略
- **强制原则**: 普通用户之间的数据、任务、标签完全隔离
- **实现要求**: 所有数据查询必须包含用户身份过滤
- **管理员权限**: 可查看所有用户数据，但需有审计日志

### 标签版本管理
- 标签修改采用**版本追加**模式，不覆盖历史版本
- 每个任务绑定标签的具体版本（标签ID + 版本号）
- 确保历史任务的可追溯性和结果一致性

## 关键数据模型

### 用户 (User)
- id, username, password_hash, role (admin/normal), created_at

### 标签 (Label)
- id, user_id, name, version, description, focus_columns (JSON), created_at
- **唯一约束**: (user_id, name, version)
- **当前版本查询**: 每个 (user_id, name) 组合的最大 version

### 文件任务 (FileTask)
- id, user_id, filename, file_path, status (uploaded/processing/completed/failed/archived)
- total_rows, processed_rows, failed_rows, created_at, archived_at

### 任务-标签关联 (TaskLabel)
- id, task_id, label_id, label_version
- **关键**: 记录执行时使用的标签版本

### 数据行结果 (DataRow)
- id, task_id, row_index, original_data (JSON), label_results (JSON)
- label_results 格式: `{"标签名_v版本号": "是/否"}`
- **优化建议**: 大数据量时考虑分表或 NoSQL 存储

## 核心业务流程

### 1. 文件上传与预览
```
上传文件 → 解析前20行 → 保存文件/任务记录 → 返回预览JSON
```
- 使用 Apache POI 解析 Excel（.xlsx/.xls），CSV 可用 Hutool CSV 或流式读取解析
- 支持 .xlsx, .csv, .xls 格式
- 预览数据用于确认列与内容；正式分析可复用已保存的文件路径再次解析

### 2. 分析任务执行（异步）
```
选择标签 → 创建任务记录 → 后台队列处理 → 逐行调用大模型 → 更新进度 → 保存结果
```

**实现要点**:
- MVP：使用 Spring `@Async` + `ThreadPoolTaskExecutor` 执行异步分析任务（无需引入外部队列）
- 每处理完一行立即更新 `processed_rows` 字段
- 前端通过 WebSocket/SSE 或轮询获取进度
- 大模型调用需要重试机制（网络/模型不稳定）

**大模型提示词构造**:
```text
你是数据标注助手。请根据以下规则判断这行数据是否符合标签定义。

标签名称: <label.name>
标签规则: <label.description>
重点关注列: <label.focus_columns 可选>

数据行内容(JSON):
<row_data_json>

请仅回答"是"或"否"，不要有任何额外解释。
```

**DeepSeek API调用规范**:
```java
// OkHttp 调用 DeepSeek OpenAI 兼容接口（示意）
Request request = new Request.Builder()
    .url(deepSeekBaseUrl + "/chat/completions")
    .addHeader("Authorization", "Bearer " + apiKey)
    .post(RequestBody.create(mediaTypeJson, requestJson))
    .build();
```

**API调用注意事项**:
- 使用低温度参数（0.1-0.3）确保结果一致性
- 设置合理的超时时间（建议30秒）
- 实现重试机制（最多3次，指数退避）
- 监控API调用失败率和延迟
- 考虑实现结果缓存（相同数据+标签→相同结果）

### 3. 结果修正与归档
- 前端表格组件需支持单元格编辑（仅限标签列）
- 归档操作将 status 改为 'archived'，设置 archived_at
- 归档后数据变为只读（前端和后端都要强制）

### 4. 数据库同步
- 动态构建 INSERT/UPDATE SQL
- 需要字段映射界面（列名 → 表字段）
- 使用事务保证原子性
- 提供回滚机制

## 开发注意事项

### 性能优化
1. **大文件处理**:
   - 超过 10000 行的文件应使用流式处理
   - 考虑分批标注（如每批 100 行）

2. **数据库查询**:
   - 任务列表必须分页
   - DataRow 表需要索引 (task_id, row_index)

3. **前端渲染**:
   - 数据表格使用虚拟滚动（超过 1000 行时）
   - 进度更新不超过每秒 2 次

### 安全性
- 文件上传需要：
  - 文件类型白名单验证
  - 文件大小限制（建议 50MB）
  - 文件名消毒（防止路径遍历）

- 数据库同步功能需要：
  - SQL 注入防护（使用参数化查询）
  - 限制可同步的表（白名单机制）

### 错误处理
- 大模型调用失败时应：
  1. 记录错误日志（包含行索引和数据摘要）
  2. 标记该行为"处理失败"
  3. 继续处理下一行（不中断整个任务）
  4. 提供"重新处理失败行"功能

### 测试策略
- **单元测试**: 标签匹配逻辑、权限检查
- **集成测试**: 完整的上传-标注-导出流程
- **压力测试**: 大文件处理性能
- **大模型测试**: 使用 mock 替代真实调用

## 前端关键组件设计

### 标签选择器
- 支持多选
- 显示标签当前版本
- 可查看标签描述（hover 提示）

### 数据表格
- 固定表头
- 标签列高亮显示
- 可编辑单元格（下拉选择"是/否"）
- 支持导出选中行

### 进度指示器
- 显示百分比和处理行数
- 可取消任务
- 失败行数提示

### 字段映射界面
- 左侧：文件列名（含标签列）
- 右侧：数据库表字段下拉选择
- 支持预览映射后的样例数据

## 词频分析实现建议

基于标签为"是"的行，提取指定列的文本：
1. 使用 HanLP 分词（Java 侧本地计算）
2. 过滤停用词
3. 统计词频
4. 返回 Top N
5. 前端用 ECharts 渲染条形图（MVP 更 KISS）

## 已确定的技术选型

- **后端**: Java 8 + Spring Boot 2.7 + Maven
- **前端**: Vue 3 + Element Plus
- **数据库**: MySQL 8.0+ (支持JSON类型)
- **大模型**: DeepSeek API (OpenAI兼容接口)
- **文件存储**: 本地文件系统
- **缓存**: Redis 7+

### 关键依赖包

**Java后端 (Maven)**:
```xml
<!-- Spring Boot Starters -->
spring-boot-starter-web (2.7.x)
spring-boot-starter-data-jpa (2.7.x)
spring-boot-starter-security (2.7.x)
spring-boot-starter-data-redis (2.7.x)
spring-boot-starter-validation (2.7.x)
spring-boot-starter-websocket (2.7.x)

<!-- 数据库 -->
mysql-connector-java (8.0.x)
druid-spring-boot-starter (连接池)

<!-- 认证授权 -->
jjwt (0.9.1) - JWT支持

<!-- 数据处理 -->
poi (5.2.x) - Excel读写
poi-ooxml (5.2.x)

<!-- 中文分词 -->
hanlp (便携版 1.8.x)

<!-- HTTP客户端 -->
okhttp3 (4.9.x)

<!-- 工具类 -->
lombok (1.18.x)
hutool-all (5.8.x)
```

### 配置文件

**application.yml 配置结构**:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_labeling
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  redis:
    host: localhost
    port: 6379
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true

# DeepSeek配置
deepseek:
  api-key: your_deepseek_api_key
  base-url: https://api.deepseek.com/v1

# JWT配置
jwt:
  secret: your-jwt-secret-key
  expiration: 3600000

# 文件上传
file:
  upload-dir: ./uploads
  max-size: 52428800
```

## 开发优先级

**MVP 阶段（核心功能）**:
1. 用户登录与权限
2. 标签 CRUD + 版本管理（新增版本不影响历史任务）
3. 文件上传与预览（前 20 行）
4. 异步标注 + 进度推送（WebSocket + 轮询兜底）
5. 结果展示 + 人工修正保存 + 导出

**二期功能**:
1. 任务归档只读（前后端强制）
2. 数据库同步（字段映射）
3. 任务统计（命中数/命中率 + 图表数据）

**三期功能**:
1. 关键词/词频分析
2. 失败行重试与更细粒度错误治理
3. 表格虚拟滚动与性能优化
4. 缓存/限流/分布式任务队列（确有规模需求再引入）
