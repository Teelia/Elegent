# 变更提案: 系统内置全局标签（仅管理员维护）

## 需求背景
当前“新建分析任务”选择标签时，会请求：
- `GET /api/labels/active?scope=global`
- `GET /api/labels/active?scope=dataset&datasetId={id}`

现状问题：
1. `labels` 表中几乎没有可用的 `scope='global'` 标签；且存在历史数据 `is_active` 为 `NULL`，导致按“激活”过滤后查询为空。
2. 产品期望是“内置标签库”：所有用户、所有数据集都可见可选，但不希望普通用户创建的标签污染系统内置库。

## 变更内容
1. 定义并实现“系统内置全局标签”语义：
   - `scope=global` 表示系统内置标签库（所有用户可读可选）
   - 仅管理员（admin 角色）允许创建/更新/删除 `scope=global` 标签
2. 修复历史数据一致性：将 `labels.is_active` 的 `NULL` 统一修正为 `1`（激活），避免被过滤导致“看不到内置标签”。
3. 文档同步：更新 API 手册，明确全局标签的权限与可见性规则。

## 影响范围
- **模块:**
  - 后端：标签接口权限控制、标签查询逻辑、数据库迁移
  - 前端：无需改动（沿用现有 `getAvailableLabelsForDataset`）
  - 文档：API 手册
- **文件:**
  - `backend/src/main/java/com/datalabeling/service/LabelService.java`
  - `backend/src/main/java/com/datalabeling/controller/LabelController.java`
  - `backend/src/main/java/com/datalabeling/repository/LabelRepository.java`
  - `backend/src/main/resources/db/migration/sql/*`
  - `helloagents/wiki/api.md`
- **API:**
  - `GET /api/labels/active?scope=global`
  - `POST /api/labels`（限制 scope=global）
  - `PUT /api/labels/{id}`（限制 scope=global）
  - `DELETE /api/labels/{id}`（限制 scope=global）
- **数据:**
  - `labels.is_active` NULL → 1（数据修复）

## 核心场景

### 需求: 内置全局标签可见可选
**模块:** 新建分析任务 / 标签选择
任意登录用户在任意数据集中新建分析任务时，应能看到并选择系统内置全局标签（激活 + 最新版本）。

#### 场景: 普通用户新建任务选择内置标签
用户已登录，进入任意数据集的“新建分析任务”弹窗。
- 下拉框展示内置全局标签列表（`scope=global`）。
- 可选择并提交给后端创建任务。

### 需求: 仅管理员可维护内置全局标签
**模块:** 标签管理
普通用户不能创建/修改/删除 `scope=global` 标签；管理员可以维护内置标签库。

#### 场景: 普通用户尝试创建 scope=global
普通用户调用 `POST /api/labels` 且 `scope=global`。
- 被拒绝（返回 403 或业务错误码）。

## 风险评估
- **风险:** 权限规则变更可能影响现有使用“全局标签”的流程（如历史上普通用户曾创建 global）。
- **缓解:** 迁移/发布前扫描是否存在非 admin 用户的 `scope=global` 记录；如存在，需决定迁移策略（转为 dataset 或由管理员接管）。

