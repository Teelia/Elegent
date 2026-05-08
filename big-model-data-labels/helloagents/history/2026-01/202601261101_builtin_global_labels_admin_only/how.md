# 技术设计: 系统内置全局标签（仅管理员维护）

## 技术方案

### 核心技术
- 后端：Spring Boot + Spring Security + Spring Data JPA
- 数据库修复：SQL脚本（`backend/sql/migrations/`，按项目现有方式手工执行）

### 实现要点
1. **全局标签查询语义调整（read path）**
   - `GET /api/labels/active?scope=global`：返回“系统内置全局标签”
   - 实现方式：查询 `scope='global' AND isActive=true` 且创建者为管理员用户（role=admin）
2. **全局标签写入权限控制（write path）**
   - `POST /api/labels`：当 `scope=global` 时，仅允许 admin
   - `PUT /api/labels/{id}`、`DELETE /api/labels/{id}`：当目标标签 `scope=global` 时，仅允许 admin
3. **数据一致性修复（SQL脚本）**
   - 将 `labels.is_active` 为 `NULL` 的记录更新为 `1`
   -（可选）将 `labels.is_active` 的默认值修正为 `1`，并考虑设置为 `NOT NULL`（需评估现网数据与表结构）
4. **文档同步**
   - 更新 `helloagents/wiki/api.md` 描述新语义与权限规则

## 架构决策 ADR

### ADR-001: “内置全局标签”以管理员维护作为来源
**上下文:** 需要系统范围可见的内置标签库，同时避免普通用户污染。  
**决策:** 将 `scope=global` 定义为内置标签库，仅管理员可写；所有用户可读可选。  
**理由:** 不引入新表/新字段即可达成“内置+可维护”；成本低，风险可控。  
**替代方案:** 新增 `is_builtin` 字段或单独 builtin_labels 表 → 当前拒绝原因：需要额外迁移与前后端改动，交付周期更长。  
**影响:** 需要在写接口增加权限判定；可能需要处理历史上非 admin 创建的 global 标签。

## API设计

### [GET] /api/labels/active
**请求参数:**
- `scope`: `global` | `dataset` | `task`（可选）
- `datasetId`: scope=dataset 时必填

**变更:**
- `scope=global`：从“当前用户的全局标签”调整为“系统内置全局标签（管理员维护）”

### [POST] /api/labels
**变更:**
- 当 `scope=global`：仅 admin 可创建

### [PUT] /api/labels/{id} / [DELETE] /api/labels/{id}
**变更:**
- 当目标标签 `scope=global`：仅 admin 可更新/删除

## 数据模型

### 数据修复脚本: 修复 labels.is_active 为空导致的不可见问题
新增 SQL 脚本（手工执行），执行：
- `UPDATE labels SET is_active=1 WHERE is_active IS NULL;`

## 安全与性能
- **安全:** 强制校验角色权限（global 写接口）；读接口仍需登录，但不再按 userId 过滤。
- **性能:** 全局查询增加 join/子查询（管理员用户筛选）；数据量大时可考虑缓存或“admin_user_ids”预取。

## 测试与部署
- **测试:** 增加后端集成测试：
  - 普通用户请求 `scope=global` 可读
  - 普通用户创建 `scope=global` 被拒绝
  - 管理员创建的 `scope=global` 对所有用户可见
  - `is_active` NULL 的历史数据经迁移后可被查询到
- **部署:** 先执行数据修复脚本（如生产需先备份并走变更流程），再发布应用。
