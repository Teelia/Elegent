# 智能数据标注与分析平台（大模型数据标签）

基于 DeepSeek（OpenAI 兼容接口）的大模型数据处理平台：支持对 Excel/CSV 逐行智能打标，提供自定义标签（版本化）、异步分析与实时进度、结果人工修正、导出、归档、外部数据库同步与统计分析能力。

## 技术栈
- 前端：Vue 3 + Vite + Element Plus
- 后端：Spring Boot 2.7 + Java 8 + Spring Security（JWT）+ JPA
- 存储：MySQL 8（Redis 可选）

## 文档
- 需求：`项目需求.txt`
- 实现方案（可落地版）：`实现方案.md`
- 后端说明：`backend/README.md`
- 历史方案（含 FastAPI/Celery，仅供参考）：`实现方案-legacy-fastapi-celery.md`

## 本地运行（后端）
1. 初始化 MySQL：执行 `backend/sql/schema.sql`
2. 配置：修改 `backend/src/main/resources/application.yml`
3. 启动：在 `backend/` 目录执行 `mvn spring-boot:run`（或 `mvn clean package -DskipTests` 后运行 jar）

默认管理员账号：`admin / admin123`（生产环境请务必修改默认密码）。

