# 任务清单：身份证遮挡 number_intent

> 方案包：202601201322_id_card_masked_intent  
> 模式：标准开发（多文件改动 + 知识库同步 + 单测回归）

## 任务
- [√] 后端：`NumberEvidenceExtractor` 新增 `ID_MASKED` / `ID_INVALID_LENGTH_MASKED` 证据类型与关键词锚定
- [√] 后端：`NumberIntentEvaluator` 支持 `invalid_length_masked` 任务与身份证遮挡统计/选择逻辑
- [√] 前端：number_intent 向导支持 `invalid_length_masked`（仅身份证显示）并提示“全遮挡不计存在”
- [√] 测试：新增身份证遮挡意图单测
- [√] 验证：`mvn test`、`npm run build`
- [√] 知识库：同步更新 wiki/modules 与总体方案口径、更新 CHANGELOG
- [√] 迁移：将本方案包迁移至 `helloagents/history/2026-01/` 并更新 `history/index.md`
