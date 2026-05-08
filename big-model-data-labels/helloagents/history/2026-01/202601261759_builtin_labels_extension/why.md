# 内置全局标签扩展（涉警当事人/在校学生）- Why

## 背景
- 业务希望将《检测规则.docx》中两条核心质量规则落地为“系统内置全局标签”，供所有用户复用。
- 现有实现以 `Label.description` 作为规则文本，配合（可选）规则预处理结果 `preprocessor_result` 辅助 LLM 判断。
- 当前项目中存在历史数据 `labels.is_active` 为空，导致“激活标签查询”过滤后不可见的风险；同时部分查询条件使用 `l.isActive = true` 会把 NULL 视为 false。

## 目标
1. 在不大改表结构/不引入新字段的前提下，落地两条“内置全局标签”（管理员创建、全局可见）。
2. 补齐规则预处理能力：新增 3 个提取器（passport / keyword_match / school_info），并接入现有预处理链路。
3. 修复 `labels.is_active` 为空导致的可见性问题，并加固相关查询过滤条件。

## 非目标
- 不按 `.claude/plan/builtin-labels-extension.md` 中的 `labels.llm_config / rule_supplement` 字段原样重构（现有代码/表结构不支持）。
- 不引入新的 LLM 调度与多模型路由（保持现有 ModelConfig 机制）。

