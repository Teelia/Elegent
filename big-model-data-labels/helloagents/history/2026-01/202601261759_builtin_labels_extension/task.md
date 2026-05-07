# 内置全局标签扩展（涉警当事人/在校学生）- Task

> 状态符号: `[ ]` 待执行 / `[√]` 已完成 / `[X]` 执行失败 / `[-]` 已跳过 / `[?]` 待确认

## 开发任务清单

### A. is_active 修复与加固
- [ ] 增加 Flyway 迁移：修复 `labels.is_active` NULL，并约束为 NOT NULL + DEFAULT 1
- [ ] 修复 `LabelRepository.findAvailableLabelsForTask` 的 NULL 过滤问题（改用 `COALESCE`）
- [ ] 保留并校验 `backend/sql/migrations/20260126_fix_labels_is_active_null.sql` 的幂等性（作为手工运维脚本）

### B. 预处理器扩展
- [ ] 新增 `PassportExtractor`（`INumberExtractor`）
- [ ] 新增 `KeywordMatcherExtractor`（`INumberExtractor`）
- [ ] 新增 `SchoolInfoExtractor`（`INumberExtractor`）
- [ ] 扩展 `PreprocessorConfig`：新增 passport/keyword_match/school_info 的 options
- [ ] 扩展 `AnalysisTaskAsyncService.executePreprocessing`：接入新提取器；并补齐现有预处理输出字段名
- [ ] 扩展 `ExtractionOrchestrator`：注入并暴露新提取器 getter（供预处理链路调用）

### C. 内置全局标签自动补齐
- [ ] 新增启动初始化器：缺失则创建 2 个内置全局标签（admin 创建、scope=global）
- [ ] 为两条内置标签写入预处理配置（rule_then_llm + include_preprocessor_in_prompt=true）

### D. 测试与回归
- [ ] 单测：PassportExtractor / KeywordMatcherExtractor / SchoolInfoExtractor
- [ ] 单测：is_active 为 NULL 时的查询行为（至少覆盖 JPQL 变更处）
- [ ] 本地构建/测试通过（`mvn test`）

### E. 知识库同步（SSOT）
- [ ] 更新 `helloagents/wiki/modules/backend.md`：补充内置标签与新提取器说明、迁移说明
- [ ] 更新 `helloagents/CHANGELOG.md`

