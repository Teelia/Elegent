# 变更提案: 全局标签改为系统范围可见

## 需求背景
当前 `GET /api/labels/active?scope=global` 的实现会按“当前登录用户”过滤全局标签，导致当用户未创建任何全局标签时返回空数组；而期望行为是：全局标签应为系统范围可见的公共标签库。

## 变更内容
1. 调整 `scope=global` 的查询语义：返回系统内全部激活的全局标签（最新版本），不再限制为当前用户。
2. 保持 `scope=dataset&datasetId=...` 行为不变。
3. 同步更新 API 文档，明确 `scope=global` 的可见性语义与边界。

## 影响范围
- **模块:**
  - 后端：标签查询逻辑（LabelService / LabelRepository）
  - 文档：API 手册
- **文件:**
  - `backend/src/main/java/com/datalabeling/service/LabelService.java`
  - `backend/src/main/java/com/datalabeling/repository/LabelRepository.java`
  - `helloagents/wiki/api.md`
- **API:**
  - `GET /api/labels/active?scope=global`
- **数据:**
  - 无数据库结构变更（仅查询条件变更）

## 核心场景

### 需求: 系统全局标签可见
**模块:** 标签管理
任意登录用户调用 `GET /api/labels/active?scope=global` 时，应能获取系统范围内的全局标签（最新版本、激活状态）。

#### 场景: 普通用户查询全局标签
用户已登录。
- 返回 `scope=global` 且 `isActive=true` 的标签列表（每个 name 仅返回最新版本）。
- 不因“当前用户没有创建全局标签”而返回空数组。

## 风险评估
- **风险:** 该方案会使“任意用户创建的全局标签”对全体用户可见，可能带来越权可见性/数据污染风险。
- **缓解:** 在实施阶段补充权限策略评估与最小化变更开关（例如后续升级为“admin 维护的内置全局标签”或新增 builtin 标记）。

