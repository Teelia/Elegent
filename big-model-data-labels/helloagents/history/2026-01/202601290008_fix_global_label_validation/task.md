# Task

> 状态符号：`[ ]` 待执行 / `[√]` 已完成 / `[X]` 失败 / `[-]` 跳过 / `[?]` 待确认

## A. 逐行审计报告

- [√] 读取两份测试数据 xlsx（Task-90/Task-91），抽取必要字段
- [√] 逐行解析标签输出：结论、后置验证摘要、关键触发项
- [√] 生成审计报告 xlsx（包含问题类型、修复后预期）
- [√] 对审计结果做基本一致性校验（行数、关键字段不为空）

## B. 逻辑修复

- [√] 后置验证链路透传 `labelName`，在校学生标签启用 `StudentInfoValidator`
- [√] 修复 `IdCardLengthValidator`：空白前缀不再判为格式错误
- [√] 优化 `PartyExtractor`：过滤明显非人名的缺失名单噪声

## C. 回归测试

- [√] 新增/更新单元测试覆盖上述三处修复点
- [√] 本地运行后端测试通过

## D. 知识库同步

- [√] 更新 `helloagents/wiki/modules/backend.md`（缺陷与修复说明、回归方式）
- [√] 更新 `helloagents/CHANGELOG.md`

## E. 收尾

- [√] 迁移方案包至 `helloagents/history/YYYY-MM/`
- [√] 更新 `helloagents/history/index.md`

## 产物

- 逐行审计报告：`测试数据/audit_task90_task91_202601290017.xlsx`
