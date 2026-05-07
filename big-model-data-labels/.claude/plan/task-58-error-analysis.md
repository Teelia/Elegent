# Task 58 分析结果错误原因分析

## 一、问题现象

### Excel文件信息
- 文件: `analysis-task-58-results-with-reasoning.xlsx`
- 标签名: `涉警当事人信息完整性检查- 误获取_v1`
- 所有行的结论: **都是"否"**
- 推理依据: 显示 `number_intent: entity=id_card, task=invalid`

### 示例数据分析

| 行号 | 原始数据摘要 | 结论 | 推理依据 |
|------|-------------|------|----------|
| 0 | 涉警纠纷双方当事人（身份证号340121197707234933...） | **否** | entity=id_card, task=invalid, valid=2, invalid=0 |
| 1 | 纠纷，身份证号341223197806125133... | **否** | entity=id_card, task=invalid, valid=1, invalid=0 |
| 2 | 单位报警，双方当事人身份证号342422196307107070... | **否** | entity=id_card, task=invalid, valid=2, invalid=0 |

---

## 二、根本原因

### 问题1: 标签配置错误

**使用的标签配置** (从推理依据反推):
```json
{
  "preprocessingMode": "RULE_ONLY",
  "preprocessorConfig": {
    "number_intent": {
      "entity": "id_card",
      "task": "invalid"
    }
  }
}
```

**应该使用的内置标签配置**:
```json
{
  "preprocessingMode": "RULE_THEN_LLM",
  "preprocessorConfig": {
    "extractors": ["id_card", "passport", "keyword_match"],
    "extractorOptions": {
      "id_card": {"include18Digit": true},
      "passport": {"include_cn_only": false},
      "keyword_match": {
        "keywords": ["请求撤警", "误报警", "重复警情", "无效警情"...],
        "matchType": "any"
      }
    }
  }
}
```

### 问题2: 执行路径错误

**实际执行路径** (错误):
```
processClassificationLabel
  ↓
label.isRuleOnlyMode() = true
  ↓
cfg.getNumberIntent() != null
  ↓
numberIntentEvaluator.evaluate(text, numberIntentConfig)
  ↓
entity=id_card, task=invalid
  ↓
提取无效身份证号
  ↓
没有找到无效身份证号 → 结论"否"
```

**应该执行路径** (正确):
```
processClassificationLabel
  ↓
label.isHybridMode() = true
  ↓
executePreprocessing() → 提取身份证号、护照号、关键词
  ↓
deepSeekService.judge() → 将规则证据传给LLM
  ↓
LLM根据标签描述和规则证据综合判断
  ↓
如果置信度<75%，触发二次强化
```

### 问题3: 标签名称异常

标签名为 `涉警当事人信息完整性检查- 误获取_v1`，后缀 `- 误获取` 暗示这不是真正的内置标签，而是用户创建的自定义标签，配置了错误的 `number_intent`。

---

## 三、number_intent 配置含义

`number_intent` 是用于**号码类结构化提取**的配置，不是用于**信息完整性检查**的：

| entity | task | 用途 |
|--------|------|------|
| id_card | exists | 检查是否存在身份证号 |
| id_card | extract | 提取所有身份证号 |
| id_card | **invalid** | **提取无效/错误的身份证号** ← 当前配置 |
| id_card | masked | 提取脱敏的身份证号 |

**当前配置 `task=invalid` 的含义**:
- 只提取格式错误的身份证号（如长度不对）
- **不检查信息是否完整**
- **不检查当事人是否都有身份证号**

所以即使身份证号都是有效的，也会因为"没有无效身份证号"而返回"否"。

---

## 四、为什么判断依据是错误的

### 原始数据0分析

**内容**: 涉警纠纷双方当事人，其中一方身份证号340121197707234933（18位，格式正确），另一方34040419971118021X（18位，末位X，格式正确）

**当前判断逻辑** (错误):
1. task=invalid → 查找无效身份证号
2. valid=2, invalid=0 → 两个身份证号都是有效的
3. 没有找到无效身份证号 → 结论"否"

**应该的判断逻辑** (正确):
1. 规则提取: 提取到2个有效身份证号
2. LLM判断: 双方当事人都有有效身份证号 → 信息完整 → 结论"是"

### 原始数据1分析

**内容**: 纠纷当事人，有身份证号341223197806125133

**当前判断逻辑** (错误):
1. task=invalid → 查找无效身份证号
2. valid=1, invalid=0 → 身份证号有效
3. 没有找到无效身份证号 → 结论"否"

**应该的判断逻辑** (正确):
1. 规则提取: 提取到1个有效身份证号
2. LLM判断: 当事人有有效身份证号 → 信息完整 → 结论"是"

---

## 五、解决方案

### 方案1: 使用正确的内置标签

1. 删除错误的 `number_intent` 配置
2. 使用真正的内置标签: `涉警当事人信息完整性检查`
3. 确保 `preprocessingMode` 为 `RULE_THEN_LLM`

### 方案2: 修正标签配置

如果需要自定义标签，应该配置为:
```json
{
  "preprocessingMode": "RULE_THEN_LLM",
  "preprocessorConfig": {
    "extractors": ["id_card", "passport", "keyword_match"],
    "extractorOptions": {
      "keyword_match": {
        "keywords": ["请求撤警", "误报警", "重复警情"],
        "matchType": "any"
      }
    }
  },
  "includePreprocessorInPrompt": true,
  "enableEnhancement": true,
  "enhancementConfig": {
    "triggerConfidence": 75
  }
}
```

### 方案3: 代码层面修复

在 `BuiltinGlobalLabelsInitializer` 中，确保初始化的内置标签:
1. **不包含** `number_intent` 配置
2. **必须包含** `extractors` 配置
3. **preprocessingMode** 必须为 `RULE_THEN_LLM`

---

## 六、验证步骤

1. **检查数据库中的标签配置**:
```sql
SELECT id, name, preprocessing_mode, preprocessor_config
FROM labels
WHERE name LIKE '%涉警当事人信息完整性%';
```

2. **确认配置是否正确**:
- `preprocessing_mode` 应该是 `rule_then_llm`
- `preprocessor_config` 应该包含 `extractors` 数组
- **不应该**包含 `number_intent` 字段

3. **重新创建分析任务**:
- 使用正确的内置标签ID
- 或修正标签配置后再创建任务

---

## 七、相关文件位置

| 文件 | 说明 |
|------|------|
| [BuiltinGlobalLabelsInitializer.java](backend/src/main/java/com/datalabeling/service/BuiltinGlobalLabelsInitializer.java) | 内置标签初始化器 |
| [AnalysisTaskAsyncService.java:461-514](backend/src/main/java/com/datalabeling/service/AnalysisTaskAsyncService.java) | processRuleOnlyLabel方法 |
| [NumberIntentEvaluator.java](backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java) | number_intent评估器 |
| [NumberIntentConfig.java](backend/src/main/java/com/datalabeling/dto/NumberIntentConfig.java) | number_intent配置类 |
| [Label.java](backend/src/main/java/com/datalabeling/entity/Label.java) | Label实体类 |
