# 任务清单: 前端交互重排与内置“错误身份证(长度错)”模板

目录: `helloagents/plan/202601211058_frontend_ui_simplify_id_invalid/`

---

## 1. 前端：全局布局与页面信息架构
- [√] 1.1 在 `frontend/src/App.vue` 中简化头部/侧边栏样式与分区，统一页面Header区域，为各页面提供一致布局框架，验证 why.md#需求-新建-编辑标签-模板优先-场景-普通用户快速创建错误身份证-长度错-标签
- [√] 1.2 在 `frontend/src/styles/*` 中补充统一的间距/字号/色板变量（不追求炫酷效果，追求一致性），验证 why.md#需求-提取器配置全局可见但默认收纳

## 2. 前端：标签配置重构（模板优先 + 高级折叠）
- [√] 2.1 在 `frontend/src/components/DatasetLabelManager.vue` 中把配置拆成 Steps/Tabs：基础信息 → 模板选择 → 高级配置(折叠)，验证 why.md#需求-新建-编辑标签-模板优先-场景-普通用户快速创建错误身份证-长度错-标签
- [√] 2.2 新增模板：错误身份证(长度错)-是否存在 / 提取，选择后自动填充 label.type / preprocessingMode / preprocessorConfig(number_intent)，并默认 policy.defaultMaskedOutput=false，验证 why.md#需求-错误身份证号口径统一-长度错误-场景-规则闭环输出明文并提供证据
- [√] 2.3 高级配置区：保留“提取器选择/选项/JSON编辑/强化分析”，但默认折叠；当模板启用时给出提示并避免强制要求选择提取器，验证 why.md#需求-提取器配置全局可见但默认收纳-场景-管理员维护提取器不影响普通用户

## 3. 前端：提取器配置页收纳
- [√] 3.1 在 `frontend/src/views/ExtractorsView.vue` 中把低频区（AI生成、options）折叠到 Collapse，并增加内置/自定义默认筛选，验证 why.md#需求-提取器配置全局可见但默认收纳-场景-管理员维护提取器不影响普通用户

## 4. 前端：任务创建与结果复核（证据抽屉）
- [√] 4.1 在任务创建页（如 `frontend/src/views/TasksView.vue` 或相关组件）为模板标签增加标识，并优化标签选择交互，验证 why.md#需求-新建-编辑标签-模板优先-场景-普通用户快速创建错误身份证-长度错-标签
- [√] 4.2 在 `frontend/src/components/LabelResultsReview.vue` 中新增“证据”抽屉：展示 reasoning + extractedData(items/counts/needs_review)，默认明文，提供脱敏切换，验证 why.md#需求-结果复核展示规则证据-SSOT-场景-复核人员依据证据快速确认错误身份证-长度错-命中

## 5. 后端：口径与证据输出调整（配合前端）
- [√] 5.1 在 `backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java` 中按口径调整：
  - entity=id_card 时 invalid/extract/exists 仅基于 ID_INVALID_LENGTH
  - 遮挡不计入 exists，不进入输出集合
  - 18位校验位不通过不视为错误（不输出 ID_INVALID_CHECKSUM）
  - 默认明文输出（defaultMaskedOutput=false）
  验证 why.md#需求-错误身份证号口径统一-长度错误-场景-规则闭环输出明文并提供证据
- [√] 5.2 如后端返回的 LabelResultVO 未透出 extractedData，补齐 DTO/序列化映射，确保前端可获取 extractedData，验证 why.md#需求-结果复核展示规则证据-SSOT-场景-复核人员依据证据快速确认错误身份证-长度错-命中
  > 备注: LabelResultVO 已支持 extractedData 解析；本次补齐的是 rule_only 分类场景也写入 extracted_data_json，确保“是否存在”也可展示结构化证据。

## 6. 安全检查
- [√] 6.1 执行安全检查（按G9：默认明文展示的UI提示、导出提示、避免日志落明文）

## 7. 文档更新
- [√] 7.1 更新 `helloagents/wiki/modules/frontend.md`：记录“模板优先 + 证据抽屉”规范与入口变更
- [√] 7.2 更新 `helloagents/wiki/modules/backend.md`：记录“错误身份证(长度错)口径”与 number_intent 行为
- [√] 7.3 更新 `helloagents/CHANGELOG.md`：记录本次改动

## 8. 测试
- [√] 8.1 后端新增单元测试：NumberIntentEvaluator（id_card invalid 仅 length；masked/ checksum 不计）
- [?] 8.2 前端回归清单：新建模板标签→创建任务→查看结果→打开证据抽屉→导出提示
  > 备注: 已通过 `npm run build` 做编译回归；建议人工走一遍“新建模板标签→创建任务→复核证据抽屉”链路确认交互细节。
