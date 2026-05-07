# frontend

## 目的
提供标签配置、任务创建、分析进度展示、结果复核与导出的可视化界面。

## 模块概述
- **职责:** Vue3 前端、任务/标签管理页面、进度 WebSocket、结果展示与导出触发。
- **状态:** 🚧开发中
- **最后更新:** 2026-01-21

## 规范

### 需求: 号码类标签提取与分析 99% 准确改造
**模块:** frontend
对“号码证据 JSON”的展示与复核提供更友好的 UI（可选），包含：脱敏展示、证据链查看、needs_review 快速筛选。
另外，标签配置支持 `number_intent` 向导：用于手机号/银行卡/身份证的“存在/提取/无效/遮挡”规则闭环（身份证支持 invalid_length_masked）。

#### 场景: needs_review 复核流
- 预期结果1：用户能看到规则证据与 LLM 解释的一致性/冲突点。
- 预期结果2：支持一键修正结论并回写。

### 需求: 前端简化 + 内置“错误身份证号（长度错）”模板
**模块:** frontend

目标：降低普通用户配置成本，保留管理员能力但默认折叠收纳。

**当前实现落点（已完成）：**
- 全局布局：`frontend/src/App.vue` + `frontend/src/styles/global.css`
  - 统一简洁 App Shell（顶栏+侧栏），全局色板/间距/字号变量收敛。
- 标签配置重排：`frontend/src/components/DatasetLabelManager.vue`
  - “内置模板(默认) + 基础信息 + 高级配置(折叠)”的信息架构。
  - 内置模板：
    - 错误身份证号（长度错）- 是否存在（分类：是/否）
    - 错误身份证号（长度错）- 提取（提取：默认明文+证据）
  - 模板会写入 `labels.preprocessor_config` 的 `number_intent`，并写入 `_meta.template` 便于识别。
- 提取器配置收纳：`frontend/src/views/ExtractorsView.vue`
  - 默认筛选“内置”，低频配置折叠到 Collapse（保留但不干扰普通用户）。
- 任务创建标签选择：`frontend/src/views/DatasetDetailView.vue`
  - 对带 `_meta.template` 的标签在下拉中展示“内置”标识。
- 结果复核证据抽屉：`frontend/src/components/LabelResultsReview.vue`
  - “证据”抽屉展示推理(reasoning) + 结构化证据(extractedData.items/counts/needs_review)。
  - 默认明文展示，并提供“脱敏”开关与敏感提示。

## API接口
详见 `helloagents/wiki/api.md`。

## 数据模型
详见 `helloagents/wiki/data.md`。

## 依赖
- Element Plus
- ECharts
- STOMP/SockJS（进度推送）

## 变更历史
- [202601192135_num_extraction_99](../../history/2026-01/202601192135_num_extraction_99/) - 号码类标签提取与分析 99% 准确改造

## 2026-01-26 更新

- 新增内置标签页面与详情弹窗（BuiltinLabelsView / BuiltinLabelDetailDialog）
- 新增内置标签API封装（frontend/src/api/builtin-labels.ts）
- 路由与入口调整（App.vue / router/index.ts / DatasetLabelManager.vue）

