# 技术设计: 前端交互重排与内置“错误身份证(长度错)”模板

## 技术方案

### 核心技术
- 前端：Vue 3 + Element Plus + Vite
- 后端：Spring Boot + JPA + MySQL(JSON列)

### 设计目标
1. “模板优先”：普通用户默认走模板配置，不暴露低频字段。
2. “高级可达”：提取器/JSON/强化等能力保留，但默认折叠收纳。
3. “证据闭环”：规则侧 SSOT 写入 label_results.extracted_data（JSON），前端可视化展示。
4. “口径一致”：错误身份证仅长度错误（ID_INVALID_LENGTH），不含校验位错误，不含遮挡。
5. “默认明文”：前端默认展示明文，但提供显式的“脱敏切换”按钮与提示。

### UI/交互重排（方案2要点）

#### 1) 全局布局（App Shell）
- 侧边栏：改为“少分组、短标题”，减少二级文案；保留入口但避免“配置优先”的心智。
- 顶部：保留用户信息与退出；减少装饰性样式，统一间距与字号。
- 主内容：统一页面 Header（标题+主按钮+辅助动作），表格区与侧边抽屉（详情/证据）采用一致交互。

#### 2) 标签配置（新建/编辑）
将原来的“大弹窗单表单”拆成三个可复用子组件（仍可在一个 Dialog 内以 Steps/Tabs 呈现）：
- Step1 基础信息：名称/描述/关注列
- Step2 选择模板（默认）：
  - 模板：错误身份证(长度错)
    - 子类型：是否存在（classification）
    - 子类型：提取（extraction）
  - 选择后自动生成：
    - preprocessingMode=rule_only
    - preprocessorConfig.number_intent = { entity:'id_card', task:'invalid', include:['invalid'], policy:{ defaultMaskedOutput:false }, output:{...} }
  - 注意：按口径 invalid 只输出 ID_INVALID_LENGTH（后端实现约束），遮挡不参与。
- Step3 高级配置（折叠）：
  - 提取器选择（extractors + options）：保留，但默认折叠；当模板已选时提示“模板已覆盖，修改可能导致口径变化”。
  - JSON 编辑：保留，但默认折叠；提供“从当前表单生成 JSON”只读预览。
  - 强化分析：折叠；默认关闭。

#### 3) 提取器配置页（全局可见）
- 保留现有能力（正则/选项/AI生成），但把低频区（AI生成、选项）折叠到 Collapse。
- 增加“内置/自定义”视图分离与默认筛选（优先看到内置）。

#### 4) 任务创建与结果复核
- 任务创建：标签选择处增加“模板标签”标识（chip），并引导用户优先选择模板标签。
- 结果复核：
  - 列表行新增“证据”按钮，打开 Drawer
  - Drawer 内容：
    - 规则摘要（reasoning）
    - extracted_data.items：候选列表（value 明文/脱敏切换、type、confidenceRule、keywordHint）
    - counts/needs_review

## 架构决策 ADR

### ADR-001: 证据通过 label_results.extracted_data 传递（不新增表）
**上下文:** 需要前端展示规则证据，但不希望引入新表或大范围API改造。
**决策:** 复用 label_results.extracted_data(JSON) 存放证据结构（items/counts/needs_review等）。
**理由:** 最小化数据库与接口改动；现有实体已支持 JSON 字段。
**替代方案:** 新增 evidence 表/独立接口；拒绝原因：改动大、交付慢。
**影响:** 前端需解析 extractedData 并做容错；后端需确保 JSON 结构稳定并版本化。

### ADR-002: “错误身份证(长度错)”走 number_intent + rule_only
**上下文:** 该任务可确定性计算，不应依赖 LLM。
**决策:** 模板默认 preprocessingMode=rule_only，使用 number_intent 直接产出 exists/extract 与证据。
**理由:** 可回归、可审计、性能稳定。
**替代方案:** rule_then_llm；拒绝原因：引入幻觉与不稳定，且对该任务无必要。

## API设计

### 方案A（优先）：复用现有 /label-results 列表返回 extractedData
- 后端确保 LabelResultVO/接口把 extracted_data(JSON) 反序列化为 extractedData 字段返回。
- 前端结果页直接展示。

### 方案B（备选）：新增 /label-results/{id}/evidence
- 仅在方案A受限时启用。

## 数据模型

### extracted_data JSON（建议结构）
```json
{
  "schema": "number_intent_v1",
  "entity": "id_card",
  "task": "invalid",
  "counts": {"valid":0,"invalid":2,"masked":0,"exists":true},
  "items": [
    {"id":"N1","type":"ID_INVALID_LENGTH","value":"3408...","masked":false,"confidenceRule":90}
  ],
  "needs_review": false
}
```

## 安全与性能
- **安全:** 默认明文展示时，在 UI 上提供“脱敏切换”与提示；导出前提示包含敏感信息。
- **性能:** rule_only 路径不调用 LLM；证据JSON体量控制（maxItems）。

## 测试与部署
- 前端：为模板选择/生成配置/证据抽屉渲染补充组件测试（如现有测试体系缺失则至少做手工回归清单）。
- 后端：为 NumberIntentEvaluator 新口径补充单元测试（遮挡不计、checksum不计、默认明文）。
- 部署：无新增基础设施；注意前后端同时发布以避免字段不一致。
