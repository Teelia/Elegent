# 内置标签实现逻辑文档

## 一、整体架构

内置标签系统采用 **"规则优先 + LLM增强"** 的混合模式：

```
原始文本 → 预处理提取器(规则) → 规则证据 → LLM判断 → 二次强化(可选) → 最终结果
```

### 核心组件

| 组件 | 文件 | 职责 |
|------|------|------|
| **BuiltinGlobalLabelsInitializer** | [BuiltinGlobalLabelsInitializer.java](backend/src/main/java/com/datalabeling/service/BuiltinGlobalLabelsInitializer.java) | 启动时初始化内置标签 |
| **ExtractionOrchestrator** | [ExtractionOrchestrator.java](backend/src/main/java/com/datalabeling/service/extraction/ExtractionOrchestrator.java) | 提取器协调器 |
| **INumberExtractor** | [INumberExtractor.java](backend/src/main/java/com/datalabeling/service/extraction/INumberExtractor.java) | 提取器接口 |
| **NumberIntentEvaluator** | [NumberIntentEvaluator.java](backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java) | 规则评估器 |

---

## 二、内置标签列表

当前系统初始化了 **2个内置全局标签**：

### 1. 涉警当事人信息完整性检查

**分类**: `person_info_integrity`

**预处理配置**:
```json
{
  "extractors": ["id_card", "passport", "keyword_match"],
  "extractorOptions": {
    "id_card": {
      "include18Digit": true,
      "include15Digit": false,
      "includeLoose": false
    },
    "passport": {
      "include_cn_only": false
    },
    "keyword_match": {
      "keywords": ["请求撤警", "误报警", "不需要处理", "已协商解决",
                   "重复警情", "副单", "无效警情", "无效报警",
                   "未发现报警情况", "现场无异常", "无警情发生"],
      "matchType": "any"
    }
  }
}
```

**二次强化**: 置信度 < 75% 时触发

**判断逻辑**:
- **直接合格**: 包含撤警/无效警情关键词 → "是"
- **合格标准**: 所有涉警当事人均有有效身份证号(18位)或护照号
- **不合格标准**: 存在当事人身份信息缺失/不完整/格式错误

### 2. 在校学生信息完整性检查

**分类**: `person_info_integrity`

**预处理配置**:
```json
{
  "extractors": ["school_info", "keyword_match", "id_card"],
  "extractorOptions": {
    "school_info": {
      "exclude_training": true
    },
    "keyword_match": {
      "keywords": ["在校学生", "学生", "学号", "年级",
                   "幼儿园", "小学", "中学", "初中", "高中",
                   "中职", "技校", "学院", "大学"],
      "matchType": "any"
    },
    "id_card": {
      "include18Digit": true,
      "include15Digit": true,
      "includeLoose": true
    }
  }
}
```

**二次强化**: 置信度 < 70% 时触发

**判断逻辑**:
- **不涉及学生** → "是"
- **涉及学生且信息完整** (姓名、身份证号、学校全称、在读年级、院系/专业、联系方式) → "是"
- **涉及学生且信息不完整** → "否"

---

## 三、提取器实现详解

### 3.1 提取器接口

所有提取器实现 `INumberExtractor` 接口：

```java
public interface INumberExtractor {
    List<ExtractedNumber> extract(String text, Map<String, Object> options);
    String getExtractorType();
}
```

### 3.2 关键词匹配提取器

**文件**: [KeywordMatcherExtractor.java](backend/src/main/java/com/datalabeling/service/extraction/KeywordMatcherExtractor.java)

**用途**: 为LLM提供关键词匹配证据

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `keywords` | List/String | 关键词列表 |
| `matchType` | String | `any`(任一匹配) / `all`(全部匹配) |
| `caseSensitive` | Boolean | 是否区分大小写(默认false) |

**输出示例**:
```json
{
  "field": "关键词匹配",
  "value": "请求撤警",
  "confidence": 0.85,
  "validation": "命中关键词",
  "startIndex": 10,
  "endIndex": 14
}
```

---

### 3.3 护照号提取器

**文件**: [PassportExtractor.java](backend/src/main/java/com/datalabeling/service/extraction/PassportExtractor.java)

**用途**: 提取中国护照号和通用护照号

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `include_cn_only` | Boolean | 是否只提取中国护照(默认false) |

**识别规则**:
```java
// 中国护照: E/G/P/S/D 开头 + 8位数字
Pattern PASSPORT_CN = Pattern.compile("(?<![A-Za-z0-9])[EGDPS]\\d{8}(?![A-Za-z0-9])");

// 通用护照: 1-2位字母 + 6-9位数字
Pattern PASSPORT_GENERIC = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{1,2}\\d{6,9}(?![A-Za-z0-9])");
```

**输出示例**:
```json
{
  "field": "护照号",
  "value": "E12345678",
  "confidence": 0.90,
  "validation": "中国护照号（9位，E开头）"
}
```

---

### 3.4 学校信息提取器

**文件**: [SchoolInfoExtractor.java](backend/src/main/java/com/datalabeling/service/extraction/SchoolInfoExtractor.java)

**用途**: 识别学校类型和名称，排除培训机构

**参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| `exclude_training` | Boolean | 是否排除培训机构(默认true) |

**识别规则**:
```java
// 培训机构关键词
Pattern TRAINING_KEYWORDS = Pattern.compile("培训|补习|辅导|教育培训|培训班|培训学校|培训机构|新东方|达内|中公教育|华图教育");

// 学校类型优先级: 大学/学院 > 职业 > 高中 > 初中/中学 > 小学 > 幼儿园
Pattern UNIVERSITY = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:大学|学院))");
Pattern VOCATIONAL = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:中职|中专|技校|职校|职业学院|职业学校))");
Pattern SENIOR_HIGH = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:高中|高级中学))");
// ... 更多
```

**输出示例**:
```json
{
  "field": "学校信息",
  "value": "{\"学校类型\":\"university\",\"学校名称\":[\"北京大学\"],\"证据\":[\"命中大学/学院关键词\"]}",
  "confidence": 0.85
}
```

---

## 四、提取结果数据结构

**文件**: [ExtractedNumber.java](backend/src/main/java/com/datalabeling/service/extraction/ExtractedNumber.java)

```java
public class ExtractedNumber {
    private String field;        // 提取字段名
    private String value;        // 提取的值
    private float confidence;    // 置信度 0-1
    private String validation;   // 验证信息
    private Integer startIndex;  // 起始位置
    private Integer endIndex;    // 结束位置
}
```

---

## 五、处理流程

### 5.1 规则优先预处理流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 解析 preprocessorConfig                                   │
│    - extractors: 提取器列表                                   │
│    - extractorOptions: 各提取器参数                           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 依次调用提取器                                             │
│    for each extractor in extractors:                         │
│      results.add(extractor.extract(text, options))           │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 汇总规则证据                                               │
│    - 按字段分组提取结果                                       │
│    - 生成可审计的推理过程                                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 将规则证据注入 LLM Prompt                                  │
│    (includePreprocessorInPrompt = true 时)                   │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 二次强化流程

```
┌─────────────────────────────────────────────────────────────┐
│ 条件判断: initial_confidence < triggerConfidence             │
└─────────────────────────────────────────────────────────────┘
                              ↓ 是
┌─────────────────────────────────────────────────────────────┐
│ 调用二次强化 Prompt                                           │
│ - 输入: 原始数据 + 初步结果 + 规则证据                         │
│ - 输出: final_result, final_confidence, validation_notes     │
└─────────────────────────────────────────────────────────────┘
```

---

## 六、标签配置字段说明

### Label 实体关键字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `preprocessingMode` | String | 预处理模式: `RULE_THEN_LLM` |
| `preprocessorConfig` | JSON | 提取器配置 |
| `includePreprocessorInPrompt` | Boolean | 是否将规则证据注入Prompt |
| `enableEnhancement` | Boolean | 是否启用二次强化 |
| `enhancementConfig` | JSON | 强化配置(触发阈值、Prompt ID) |
| `builtinLevel` | String | 内置级别: `SYSTEM` |
| `builtinCategory` | String | 分类: `person_info_integrity` 等 |

### preprocessorConfig 结构

```json
{
  "extractors": ["id_card", "passport", "keyword_match"],
  "extractorOptions": {
    "提取器代码": {
      // 提取器特定参数
    }
  }
}
```

### enhancementConfig 结构

```json
{
  "triggerConfidence": 75,
  "promptId": 123
}
```

---

## 七、内置标签分类

当前定义的分类（可扩展）：

| 代码 | 名称 | 说明 |
|------|------|------|
| `person_info_integrity` | 人员信息完整性 | 身份证、护照、学生信息等 |
| `case_feature` | 案件特征 | 案件相关特征提取 |
| `data_quality` | 信息质量 | 数据完整性检查 |
| `behavior_pattern` | 行为模式 | 行为模式识别 |

---

## 八、扩展新内置标签步骤

1. **在 `BuiltinGlobalLabelsInitializer` 中添加初始化代码**
2. **配置提取器列表和参数**
3. **编写标签描述规则**
4. **(可选) 创建二次强化 Prompt 模板**
5. **选择合适的分类**

示例:
```java
ensureBuiltinLabel(
    adminId,
    "新标签名称",
    buildLabelDescription(),
    buildPreprocessorConfigJson(),
    buildEnhancementConfigJson(70, enhancementPromptId),
    "category_code"
);
```
