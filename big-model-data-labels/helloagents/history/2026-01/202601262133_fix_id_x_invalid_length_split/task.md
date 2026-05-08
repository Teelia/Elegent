# 修复 X 结尾身份证号被拆分为 17 位导致 invalid_length 误判 - Task

> 状态符号: `[ ]` 待执行 / `[√]` 已完成 / `[X]` 执行失败 / `[-]` 已跳过 / `[?]` 待确认

## 任务清单（轻量迭代）

- [√] 修复 `NumberEvidenceExtractor`：遮挡银行卡候选必须包含 `*`；纯数字候选避免匹配 X 结尾身份证的 17 位前缀
- [√] 补充/修正单测：X 结尾身份证识别与 number_intent invalid 误判回归
- [√] 验证：执行 `mvn -DskipTests=false test` 通过

