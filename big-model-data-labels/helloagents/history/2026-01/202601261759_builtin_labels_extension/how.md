# 内置全局标签扩展（涉警当事人/在校学生）- How

## 落地策略（适配现有实现）

### 1) 内置全局标签的承载方式
- 使用现有 `labels` 表与 `Label` 实体字段：
  - `scope=global` 且创建者为 admin（由现有接口权限控制）
  - `type=classification`
  - 规则文本写入 `description`
  - 规则预处理配置写入 `preprocessor_config`
  - 预处理模式设置为 `rule_then_llm`，并将预处理结果注入 LLM 提示词（`include_preprocessor_in_prompt=true`）
- 通过后端启动初始化器确保“缺失则自动创建”（避免依赖手工 SQL）。

### 2) 规则预处理能力扩展
- 新增 3 个规则提取器（实现 `INumberExtractor`）：
  - `PassportExtractor`：护照号（中国护照优先，通用护照可配置开关）
  - `KeywordMatcherExtractor`：关键字命中（支持 any/all）
  - `SchoolInfoExtractor`：识别培训机构/学校类型/学校名称（最小可用集）
- 接入位置：`AnalysisTaskAsyncService.executePreprocessing` 现有链路
  - 同时修复现有预处理输出 `field` 为空的问题（身份证/手机号/银行卡补齐字段名）

### 3) is_active 空值修复与查询加固
- 增加 Flyway 迁移脚本：
  - 先 `UPDATE labels SET is_active=1 WHERE is_active IS NULL;`
  - 再 `ALTER TABLE labels ... is_active NOT NULL DEFAULT 1;`
- 加固存在 `l.isActive = true` 的 JPQL 查询为 `COALESCE(l.isActive, true) = true`，避免 NULL 被当作 false 过滤。

## 风险与规避
- ⚠️ 不确定因素：数据集列名可能不一致（例如是否存在“警情内容/当事人信息/备注”）。
  - 决策：内置标签默认不强制限制 `focus_columns`（为空则扫描全部列），避免因列名差异导致漏判；管理员可后续在界面中为特定数据集优化关注列。
- 预处理关键词/学校规则属于启发式：仅用于辅助 LLM，不作为最终裁决的唯一依据。

