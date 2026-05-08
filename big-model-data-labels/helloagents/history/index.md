# 变更历史索引

本文件记录所有已完成变更的索引，便于追溯和查询。

---

## 索引

| 时间戳 | 功能名称 | 类型 | 状态 | 方案包路径 |
|--------|----------|------|------|------------|
| 202601291640 | party_extractor_refine_doc_alignment | 修复 | ✅已完成 | 2026-01/202601291640_party_extractor_refine_doc_alignment/ |
| 202601291035 | fix_police_personnel_false_negatives | 修复 | ✅已完成 | 2026-01/202601291035_fix_police_personnel_false_negatives/ |
| 202601290008 | fix_global_label_validation | 修复 | ✅已完成 | 2026-01/202601290008_fix_global_label_validation/ |
| 202601262133 | fix_id_x_invalid_length_split | 修复 | ✅已完成 | 2026-01/202601262133_fix_id_x_invalid_length_split/ |
| 202601261101 | builtin_global_labels_admin_only | 功能/改造 | ✅已完成 | 2026-01/202601261101_builtin_global_labels_admin_only/ |
| 202601211058 | frontend_ui_simplify_id_invalid | 功能/改造 | ✅已完成 | 2026-01/202601211058_frontend_ui_simplify_id_invalid/ |
| 202601192135 | num_extraction_99 | 修复/重构 | ✅已完成 | 2026-01/202601192135_num_extraction_99/ |
| 202601200006 | phone_bank_number_intent_99 | 功能/改造 | ✅已完成 | 2026-01/202601200006_phone_bank_number_intent_99/ |
| 202601201322 | id_card_masked_intent | 功能/改造 | ✅已完成 | 2026-01/202601201322_id_card_masked_intent/ |
| 202601201420 | fix_19_digit_id_miss | 修复 | ✅已完成 | 2026-01/202601201420_fix_19_digit_id_miss/ |
| 202601192326 | phone_bank_label_99 | 规划/草案 | [-]未执行 | 2026-01/202601192326_phone_bank_label_99/ |
| 202601261019 | system_global_labels | 规划/草案 | [-]未执行（需求变更） | 2026-01/202601261019_system_global_labels/ |
| 202601261759 | builtin_labels_extension | 规划/草案 | [-]已归档（未执行） | 2026-01/202601261759_builtin_labels_extension/ |

---

## 按月归档

### 2026-01

- [202601291640_party_extractor_refine_doc_alignment](2026-01/202601291640_party_extractor_refine_doc_alignment/) - 收敛涉警当事人抽取：修复纠纷抽取人名截断，过滤非姓名token，移除商家/网友/中介等泛化隐含当事人推断；回归千问32B验证（已完成）
- [202601291035_fix_police_personnel_false_negatives](2026-01/202601291035_fix_police_personnel_false_negatives/) - 按《检测规则.docx》对齐“涉警当事人信息完整性检查”后置规则与二次强化提示词，补充否->是纠偏并回归千问32B验证（已完成）
- [202601290008_fix_global_label_validation](2026-01/202601290008_fix_global_label_validation/) - 修复全局标签（涉警/在校学生）后置验证链路与系统性误判，并生成逐行审计报告（已完成）
- [202601262133_fix_id_x_invalid_length_split](2026-01/202601262133_fix_id_x_invalid_length_split/) - 修复 18位身份证末位X被拆分为17位导致“长度错误”误判（已完成）
- [202601261101_builtin_global_labels_admin_only](2026-01/202601261101_builtin_global_labels_admin_only/) - 系统内置全局标签（仅管理员维护，所有用户/数据集可见可选）（已完成）
- [202601211058_frontend_ui_simplify_id_invalid](2026-01/202601211058_frontend_ui_simplify_id_invalid/) - 前端简化与内置“错误身份证号（长度错）”模板/证据抽屉（已完成）
- [202601192135_num_extraction_99](2026-01/202601192135_num_extraction_99/) - 号码类标签提取与分析 99% 准确改造（已完成）
- [202601200006_phone_bank_number_intent_99](2026-01/202601200006_phone_bank_number_intent_99/) - 手机号/银行卡 number_intent（存在/提取/无效/遮挡）规则闭环改造（已完成）
- [202601201322_id_card_masked_intent](2026-01/202601201322_id_card_masked_intent/) - 身份证遮挡 number_intent（masked/invalid_length_masked，全遮挡不计存在）（已完成）
- [202601201420_fix_19_digit_id_miss](2026-01/202601201420_fix_19_digit_id_miss/) - 修复 19位错误身份证号漏检（删除一位可恢复为有效18位的近似恢复）（已完成）
- [202601192326_phone_bank_label_99](2026-01/202601192326_phone_bank_label_99/) - 手机号/银行卡标签改造草案（未执行，已归档）
- [202601261019_system_global_labels](2026-01/202601261019_system_global_labels/) - “全局标签系统范围可见”草案（需求变更未执行，已归档）
- [202601261759_builtin_labels_extension](2026-01/202601261759_builtin_labels_extension/) - 内置标签扩展草案（未执行，已归档）
