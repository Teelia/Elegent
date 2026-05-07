# 任务清单: 全局标签改为系统范围可见

目录: `helloagents/history/2026-01/202601261019_system_global_labels/`

---

## 1. 后端查询逻辑
- [-] 1.1 在 `backend/src/main/java/com/datalabeling/repository/LabelRepository.java` 中新增“系统范围全局激活标签（最新版本）”查询方法，验证 why.md#需求-系统全局标签可见-场景-普通用户查询全局标签
  > 备注: 需求变更为“系统内置全局标签（仅管理员维护）”，本方案不再执行。
- [-] 1.2 在 `backend/src/main/java/com/datalabeling/service/LabelService.java` 中调整 `scope=global` 分支，改用 1.1 的查询方法，验证 why.md#需求-系统全局标签可见-场景-普通用户查询全局标签
  > 备注: 需求变更为“系统内置全局标签（仅管理员维护）”，本方案不再执行。
- [-] 1.3（可选）在 `backend/src/main/java/com/datalabeling/controller/LabelController.java` 中补充接口注释，避免“global=当前用户”误解
  > 备注: 已在新方案中以“内置标签语义与权限”方式更新注释/文档。

## 2. 文档同步（知识库）
- [-] 2.1 更新 `helloagents/wiki/api.md`：补充 `GET /api/labels/active` 的参数说明与 `scope=global` 的系统范围语义
  > 备注: 已由新方案（内置全局标签）覆盖并执行。

## 3. 安全检查
- [-] 3.1 执行安全检查（按G9: 输入校验、权限控制、审计记录），重点确认“全局标签可见性扩大”是否符合当前产品策略
  > 备注: 风险已升级并在新方案中通过“仅管理员维护 global”规避。

## 4. 测试
- [-] 4.1 增加后端测试用例（优先集成测试）：验证不同用户创建的 `scope=global` 标签在 `scope=global` 查询中都可见；并验证 `scope=dataset` 不回归
  > 备注: 测试目标已调整为“管理员创建的 global 为内置可见 + 普通用户不可写”，见新方案测试项。
- [-] 4.2 本地运行后端测试/启动自测：手工调用 `/api/labels/active?scope=global` 与 `/api/labels/active?scope=dataset&datasetId=...` 验证结果
  > 备注: 由新方案执行并验证。
