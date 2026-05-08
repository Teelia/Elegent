# 怎么做：规则证据SSOT + number_intent 追加身份证遮挡能力

## 总体策略
- 继续沿用“规则证据建模（SSOT）→ 意图驱动（number_intent）→ 任务链路接入”的架构。
- 对身份证遮挡仅输出“部分遮挡且可锚定”的证据：
  - 关键词窗命中（身份证/证件号等）优先
  - 无关键词时仅在生日段可解析等强结构证据成立时才输出
- 全遮挡（全部*）直接忽略，不进入证据模型。

## 后端改造点
1) `NumberEvidenceExtractor`
   - 新增证据类型：
     - `ID_MASKED`
     - `ID_INVALID_LENGTH_MASKED`
   - 新增关键词组：ID（身份证号/证件号等）
   - 遮挡分支新增身份证裁决逻辑：
     - digitCount==0 → 忽略（全遮挡）
     - 命中 ID 关键词窗 或 强结构（地区码首位+生日段可解析） → 输出证据类型
   - 派生字段补齐：`id_masked_count`、`id_invalid_length_masked_count`、`id_exists` 计入遮挡类

2) `NumberIntentEvaluator`
   - 支持新任务：`invalid_length_masked`（仅对 entity=id_card 生效）
   - `masked` 任务返回所有身份证遮挡证据（含错误长度遮挡）；`invalid_length_masked` 返回子集
   - counts 统计补齐：身份证遮挡计入 masked 类别

3) 任务链路
   - `AnalysisTaskAsyncService` 已支持 `number_intent` 在 rule_only 路径优先执行，无需额外改动。

## 前端改造点
- `DatasetLabelManager.vue` 的 number_intent 向导新增任务类型：
  - `错误长度且遮挡（invalid_length_masked）`（仅 entity=id_card 时展示）
- 在 UI 提示中明确：全遮挡不计存在。

## 回归与验收
- 新增单测覆盖：
  - 部分遮挡 18位 → 命中 `masked`
  - 部分遮挡 17位 → 命中 `invalid_length_masked`
  - 全遮挡（18个*） → `exists=否`
- 通过 `mvn test` 与 `npm run build` 验证。

