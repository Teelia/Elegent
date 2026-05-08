# API 手册

## 概述
后端提供标签管理、任务管理、分析执行、结果导出等接口。实际路由以代码为准，本文件提供高层索引，便于快速定位。

## 认证方式
- 基于 JWT 的登录鉴权（详细以 `backend` 实现为准）。

---

## 接口列表（高层索引）

### 认证

#### POST /api/auth/login
**描述:** 用户登录获取 JWT。

#### POST /api/auth/logout
**描述:** 用户退出。

#### GET /api/auth/me
**描述:** 获取当前用户信息。

### 标签

#### GET /api/labels
**描述:** 获取标签列表。

#### GET /api/labels/active
**描述:** 获取激活标签（通常用于“新建分析任务”选择标签）。

**请求参数:**
| 参数名 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scope | string | 否 | `global`（系统内置全局标签，所有用户可见可选）/ `dataset`（数据集专属标签） |
| datasetId | number | 否 | scope=dataset 时必填 |
| userId | number | 否 | 管理员可指定（用于部分列表/排查场景），不影响 `scope=global` 的内置标签语义 |

#### POST /api/labels
**描述:** 创建标签（支持版本化）。

**权限:**
- `scope=global`：仅管理员可创建（作为系统内置全局标签库）
- `scope=dataset`：登录用户可创建（数据集专属）

#### GET /api/labels/{id}
**描述:** 获取标签详情。

#### PUT /api/labels/{id}
**描述:** 更新标签（通常会生成新版本）。

**权限:**
- 目标标签为 `scope=global`：仅管理员可更新

#### DELETE /api/labels/{id}
**描述:** 删除标签。

**权限:**
- 目标标签为 `scope=global`：仅管理员可删除

### 文件任务（旧链路）

#### POST /api/tasks/upload
**描述:** 上传文件并创建任务。

#### POST /api/tasks/{id}/analyze
**描述:** 启动任务分析（逐行执行标签）。

#### GET /api/tasks/{id}/export
**描述:** 导出结果。

### 分析任务（新链路，支持结构化提取/并发）

#### POST /api/analysis-tasks
**描述:** 创建分析任务（关联数据集、模型配置、并发等）。

#### POST /api/analysis-tasks/{id}/execute
**描述:** 执行分析任务（支持并发与断点续传）。

#### GET /api/analysis-tasks/{id}
**描述:** 获取分析任务状态与进度。
