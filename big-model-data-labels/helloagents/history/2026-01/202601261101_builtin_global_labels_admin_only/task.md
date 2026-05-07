# 任务清单: 系统内置全局标签（仅管理员维护）

目录: `helloagents/history/2026-01/202601261101_builtin_global_labels_admin_only/`

---

## 1. 数据修复（SQL脚本）
- [√] 1.1 在 `backend/sql/migrations/` 新增数据修复脚本：将 `labels.is_active` 为 `NULL` 的记录更新为 `1`，验证 why.md#需求-内置全局标签可见可选-场景-普通用户新建任务选择内置标签
- [-] 1.2（可选）评估并补充 `labels.is_active` 默认值/非空约束（如风险可控），避免后续再产生 NULL
  > 备注: 当前通过迁移修复存量数据 + 代码侧始终写入 true，先不做 schema 强约束以降低发布风险。

## 2. 后端：全局查询语义（系统内置）
- [√] 2.1 在 `backend/src/main/java/com/datalabeling/repository/LabelRepository.java` 增加“仅管理员创建的 global 激活最新版本”查询
- [√] 2.2 在 `backend/src/main/java/com/datalabeling/service/LabelService.java` 调整 `scope=global` 分支：使用 2.1 查询，验证 why.md#需求-内置全局标签可见可选-场景-普通用户新建任务选择内置标签

## 3. 后端：权限控制（仅管理员可维护 global）
- [√] 3.1 在 `backend/src/main/java/com/datalabeling/service/LabelService.java` 的 `createLabel` 中：当 `scope=global` 且当前用户非 admin 时拒绝，验证 why.md#需求-仅管理员可维护内置全局标签-场景-普通用户尝试创建-scope-global
- [√] 3.2 在 `backend/src/main/java/com/datalabeling/service/LabelService.java` 的 `updateLabel` / `deleteLabel` 中：当目标标签为 `scope=global` 且当前用户非 admin 时拒绝
- [√] 3.3（可选）在 `backend/src/main/java/com/datalabeling/controller/LabelController.java` 补充接口注释，避免误解

## 4. 文档同步（知识库）
- [√] 4.1 更新 `helloagents/wiki/api.md`：补充 `GET /api/labels/active` 的 `scope=global` 语义与权限规则；补充写接口的权限说明

## 5. 安全检查
- [√] 5.1 执行安全检查（按G9: 输入校验、权限控制、审计记录），重点检查：
  - 普通用户不可写 global
  - 普通用户可读 global
  - dataset 作用域不越权（至少确保查询限定 `scope='dataset'`）
  > 备注: 已将 dataset 查询限定为 `scope='dataset'` 且仅返回激活最新版本。

## 6. 测试
- [√] 6.1 新增/完善后端测试：覆盖 global 读写权限与迁移效果
- [?] 6.2 本地启动后端与前端自测：新建分析任务弹窗能看到并可选择内置标签
  > 备注: 需在目标环境执行迁移并重启服务后进行页面验证（重点看 datasetId=31 新建任务标签下拉框）。
