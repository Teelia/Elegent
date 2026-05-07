# 智能数据标注与分析平台 - 后端服务

## 📖 项目简介

基于 **DeepSeek 大模型** 的智能数据标注与分析平台后端服务，提供 Excel/CSV 文件的自动化智能标注、数据分析、关键词提取等功能。

### 核心功能

- 🤖 **智能标注**：基于自定义规则，使用 DeepSeek 大模型自动标注数据
- 📊 **数据分析**：提供多维度数据统计和可视化支持
- 🔍 **关键词提取**：基于 HanLP 的中文分词和关键词分析
- 📁 **文件处理**：支持 Excel (.xlsx, .xls) 和 CSV 文件的解析与导出
- 🔄 **数据同步**：支持将标注结果同步到外部数据库（MySQL/PostgreSQL/SQL Server）
- 👥 **多用户管理**：基于角色的访问控制，支持数据隔离
- 📝 **操作审计**：完整的操作日志记录

## 🛠️ 技术栈

### 核心框架
- **Java 8** - 编程语言
- **Spring Boot 2.7.18** - 应用框架
- **Spring Data JPA** - 数据持久化
- **Spring Security** - 安全认证
- **Maven** - 依赖管理

### 数据存储
- **MySQL 8.0+** - 主数据库
- **Redis 7+** - 可选：缓存/分布式锁/限流（MVP 可先不强依赖）

### 第三方依赖
- **DeepSeek API** - 大模型服务
- **Apache POI 5.2.5** - Excel 文件处理
- **HanLP portable-1.8.4** - 中文分词
- **Druid 1.2.20** - 数据库连接池
- **JWT 0.9.1** - 身份认证
- **OkHttp3 4.9.3** - HTTP 客户端
- **Hutool 5.8.25** - 工具库

## 📁 项目结构

```
backend/
├── src/main/java/com/datalabeling/
│   ├── DataLabelingApplication.java    # 应用启动类
│   ├── common/                          # 通用组件
│   │   ├── ApiResponse.java            # 统一响应格式
│   │   ├── ErrorCode.java              # 错误码枚举
│   │   └── PageResult.java             # 分页结果封装
│   ├── config/                          # 配置类
│   │   ├── SecurityConfig.java         # Spring Security 配置
│   │   ├── RedisConfig.java            # Redis 配置
│   │   ├── WebSocketConfig.java        # WebSocket 配置
│   │   └── DeepSeekConfig.java         # DeepSeek API 配置
│   ├── controller/                      # 控制器层
│   │   ├── AuthController.java         # 认证接口
│   │   ├── LabelController.java        # 标签管理
│   │   ├── TaskController.java         # 任务管理
│   │   └── DataRowController.java      # 数据行操作
│   ├── service/                         # 业务逻辑层
│   │   ├── UserService.java            # 用户服务
│   │   ├── LabelService.java           # 标签服务
│   │   ├── TaskService.java            # 任务服务
│   │   ├── FileProcessService.java     # 文件处理服务
│   │   ├── DeepSeekService.java        # DeepSeek API 服务
│   │   └── KeywordAnalysisService.java # 关键词分析服务
│   ├── repository/                      # 数据访问层
│   │   ├── UserRepository.java         # 用户数据访问
│   │   ├── LabelRepository.java        # 标签数据访问
│   │   ├── FileTaskRepository.java     # 任务数据访问
│   │   ├── DataRowRepository.java      # 数据行数据访问
│   │   └── ...                         # 其他 Repository
│   ├── entity/                          # 实体类
│   │   ├── BaseEntity.java             # 实体基类
│   │   ├── User.java                   # 用户实体
│   │   ├── Label.java                  # 标签实体
│   │   ├── FileTask.java               # 文件任务实体
│   │   ├── DataRow.java                # 数据行实体
│   │   └── ...                         # 其他实体
│   ├── dto/                             # 数据传输对象
│   │   ├── request/                    # 请求 DTO
│   │   └── response/                   # 响应 DTO
│   ├── converter/                       # 类型转换器
│   │   ├── JsonConverter.java          # JSON 转换器
│   │   └── StringListConverter.java    # 字符串列表转换器
│   ├── exception/                       # 异常处理
│   │   ├── BusinessException.java      # 业务异常
│   │   └── GlobalExceptionHandler.java # 全局异常处理器
│   └── util/                            # 工具类
│       ├── JwtUtil.java                # JWT 工具
│       ├── PasswordUtil.java           # 密码加密工具
│       └── FileUtil.java               # 文件工具
├── src/main/resources/
│   ├── application.yml                  # 主配置文件
│   ├── application-dev.yml             # 开发环境配置
│   └── application-prod.yml            # 生产环境配置
├── sql/
│   └── schema.sql                       # 数据库初始化脚本
└── pom.xml                              # Maven 配置文件
```

## 🚀 快速开始

### 环境要求

- **JDK 8** 或更高版本
- **MySQL 8.0+**
- **Redis 7+**
- **Maven 3.6+**

### 1. 克隆项目

```bash
git clone <repository_url>
cd backend
```

### 2. 配置数据库

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE data_labeling CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 初始化表结构
mysql -u root -p data_labeling < sql/schema.sql
```

### 3. 配置应用

编辑 `src/main/resources/application.yml` 或创建 `application-dev.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/data_labeling?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

  redis:
    host: localhost
    port: 6379
    password: # 如有密码请填写

# DeepSeek API 配置
deepseek:
  api-key: your_deepseek_api_key  # 请替换为实际的 API Key
  base-url: https://api.deepseek.com/v1
  model: deepseek-chat

# JWT 配置
jwt:
  secret: your-secret-key-change-in-production-at-least-256-bits-long  # 生产环境请修改
  expiration: 3600000  # Token 有效期（毫秒）
```

### 4. 启动 Redis

```bash
redis-server
```

### 5. 编译并启动

```bash
# 方式一：使用 Maven 直接运行
mvn clean compile
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/data-labeling-backend.jar

# 方式三：指定配置文件
java -jar target/data-labeling-backend.jar --spring.profiles.active=dev
```

### 6. 验证启动

访问以下地址验证服务是否正常启动：

- **应用健康检查**：http://localhost:8080/api/actuator/health
- **Druid 监控**：http://localhost:8080/api/druid/index.html (默认账号密码：admin/admin)

## ⚙️ 配置说明

### 核心配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | 8080 |
| `server.servlet.context-path` | 应用上下文路径 | /api |
| `spring.datasource.url` | 数据库连接地址 | - |
| `spring.redis.host` | Redis 主机地址 | localhost |
| `deepseek.api-key` | DeepSeek API 密钥 | - |
| `jwt.secret` | JWT 签名密钥 | - |
| `file.upload-dir` | 文件上传目录 | ./uploads |
| `file.max-size` | 文件最大大小（字节） | 52428800 (50MB) |

### 数据库连接池配置

```yaml
spring:
  datasource:
    druid:
      initial-size: 5          # 初始连接数
      min-idle: 5              # 最小空闲连接数
      max-active: 20           # 最大活动连接数
      max-wait: 60000          # 最大等待时间（毫秒）
```

### DeepSeek API 配置

```yaml
deepseek:
  api-key: your_api_key        # API 密钥
  base-url: https://api.deepseek.com/v1
  model: deepseek-chat         # 模型名称
  timeout: 30000               # 超时时间（毫秒）
  temperature: 0.1             # 温度参数（0-2，越低越确定性）
  max-tokens: 10               # 最大返回 Token 数
  retry-times: 3               # 失败重试次数
```

## 🔐 默认账号

**管理员账号：**
- 用户名：`admin`
- 密码：`admin123`

⚠️ **生产环境请务必修改默认密码！**

## 📚 API 文档

### 认证接口

```http
POST /api/auth/login          # 用户登录
POST /api/auth/logout         # 用户登出
GET  /api/auth/me             # 获取当前用户信息
```

### 标签管理

```http
GET    /api/labels            # 获取标签列表
POST   /api/labels            # 创建标签
GET    /api/labels/{id}       # 获取标签详情
PUT    /api/labels/{id}       # 更新标签（创建新版本）
DELETE /api/labels/{id}       # 删除标签
```

### 任务管理

```http
GET    /api/tasks             # 获取任务列表
POST   /api/tasks/upload      # 上传文件
GET    /api/tasks/{id}        # 获取任务详情
POST   /api/tasks/{id}/analyze    # 开始分析任务
GET    /api/tasks/{id}/export     # 导出结果
POST   /api/tasks/{id}/sync       # 同步到数据库
```

### 管理员接口（仅 admin）

```http
GET  /api/admin/users                     # 用户列表（分页/搜索）
POST /api/admin/users                     # 创建用户
PUT  /api/admin/users/{id}                # 更新用户（角色/启用/资料）
POST /api/admin/users/{id}/reset-password # 重置用户密码

GET  /api/admin/model-config              # 获取大模型配置（DeepSeek）
PUT  /api/admin/model-config              # 更新大模型配置（DeepSeek）
```

详细 API 文档请参考 [实现方案.md](../实现方案.md) 第 5 章节（REST API）。

## 🔧 开发指南

### 代码规范

- 使用 **Lombok** 简化实体类代码
- 遵循 **RESTful** API 设计规范
- 所有响应使用统一的 `ApiResponse<T>` 格式
- 异常处理通过 `GlobalExceptionHandler` 统一处理
- 使用 `@Valid` 和 `@Validated` 进行参数校验

### 日志规范

```java
// 使用 Slf4j 记录日志
@Slf4j
public class YourService {
    public void yourMethod() {
        log.info("业务操作开始");
        log.debug("详细调试信息: {}", details);
        log.error("发生错误", exception);
    }
}
```

### 数据库迁移

如需修改数据库结构：

1. 修改 `sql/schema.sql`
2. 创建迁移脚本（如需要）
3. 更新对应的 Entity 类
4. 测试验证

### 添加新的标注规则

1. 在 `LabelService` 中定义新的标注逻辑
2. 更新 `DeepSeekService` 中的 Prompt 模板
3. 测试验证标注准确性

## 📊 监控与运维

### 应用监控

通过 Spring Boot Actuator 提供的监控端点：

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# 应用信息
curl http://localhost:8080/api/actuator/info

# 指标数据
curl http://localhost:8080/api/actuator/metrics
```

### Druid 监控

访问 http://localhost:8080/api/druid/index.html 查看：
- SQL 监控
- 慢查询统计
- 连接池状态
- Web 应用统计

### 日志查看

日志文件位置：`logs/data-labeling.log`

```bash
# 实时查看日志
tail -f logs/data-labeling.log

# 查看错误日志
grep "ERROR" logs/data-labeling.log
```

## 🚢 生产部署

### 打包构建

```bash
# 打包（跳过测试）
mvn clean package -DskipTests

# 生成的 JAR 文件
target/data-labeling-backend.jar
```

### 系统服务配置

创建 `/etc/systemd/system/data-labeling.service`：

```ini
[Unit]
Description=Data Labeling Backend Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/data-labeling
ExecStart=/usr/bin/java -jar -Xms512m -Xmx2g -Dspring.profiles.active=prod target/data-labeling-backend.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable data-labeling
sudo systemctl start data-labeling
sudo systemctl status data-labeling
```

### 性能优化建议

1. **JVM 参数调优**
```bash
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar target/data-labeling-backend.jar
```

2. **数据库连接池优化**
   - 根据并发量调整 `max-active`
   - 设置合理的 `max-wait` 避免长时间等待

3. **Redis 缓存策略**
   - 标签列表缓存 5 分钟
   - 任务统计缓存 1 分钟

## 🐛 故障排查

### 常见问题

**1. 数据库连接失败**
```
检查：
- MySQL 服务是否启动
- 数据库账号密码是否正确
- 防火墙是否开放 3306 端口
```

**2. Redis 连接失败**
```
检查：
- Redis 服务是否启动
- Redis 密码配置是否正确
- 防火墙是否开放 6379 端口
```

**3. 文件上传失败**
```
检查：
- 上传目录是否存在且有写入权限
- 文件大小是否超过限制（默认 50MB）
- 磁盘空间是否充足
```

**4. DeepSeek API 调用失败**
```
检查：
- API Key 是否正确
- 网络是否能访问 api.deepseek.com
- API 配额是否用完
```

## 📄 许可证

[待定]

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📮 联系方式

如有问题或建议，请通过以下方式联系：
- 提交 Issue
- 发送邮件至：[your-email@example.com]

---

**版本：** 1.0.0
**最后更新：** 2025-01-17
