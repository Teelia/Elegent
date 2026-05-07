# 项目技术约定

## 技术栈
- **后端:** Java 8 / Spring Boot 2.7.x / Spring Data JPA / Spring Security（JWT）/ OkHttp
- **前端:** Vue 3 / Vite / TypeScript / Element Plus / Pinia
- **数据存储:** MySQL 8（Redis 可选）
- **大模型:** DeepSeek 70B（OpenAI 兼容接口；内网部署/内网代理均可）

## 开发约定
- **编码:** UTF-8（无 BOM）
- **日志与敏感信息:**
  - 禁止在日志、提示词、导出结果中输出完整身份证号/银行卡号/手机号；必须脱敏（例如保留前4后4，中间打星）。
  - 对“证据 JSON/建模 JSON”需要区分：存储版（可含原文片段但脱敏）与提示词版（强制脱敏）。
- **命名约定:**
  - Java 类使用 UpperCamelCase，方法与变量使用 lowerCamelCase
  - 标签类型：`classification` / `extraction` / `structured_extraction`
- **错误处理:**
  - 规则/校验失败与“未命中”区分对待；不得用“未找到”掩盖“被误分类/被冲突规则吞掉”。

## 测试与流程
- **单元测试:** 对号码提取与冲突仲裁逻辑必须覆盖边界样本（17/19/22 位、Luhn 与身份证特征冲突等）。
- **回归评估:** 固化回归集（含标注真值），输出 precision/recall/F1 与 needs_review 比例。
- **提交规范:** 建议采用 Conventional Commits（可选）。

