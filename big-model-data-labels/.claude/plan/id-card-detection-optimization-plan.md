# 身份证号码检测优化方案

> **业务口径明确**：不检测校验位，只识别长度错误和格式错误的身份证号

---

## 目录

- [一、现状分析](#一现状分析)
- [二、代码逻辑深度分析](#二代码逻辑深度分析)
- [三、优化方案](#三优化方案)
- [四、边界情况与测试用例](#四边界情况与测试用例)
- [五、小模型配合最佳实践](#五小模型配合最佳实践)
- [六、实施计划](#六实施计划)
- [七、配置参考手册](#七配置参考手册)
- [八、常见问题FAQ](#八常见问题faq)

---

## 一、现状分析

### 1.1 检测规则要求（来自检测规则.docx）

**合格标准：**
- 18位身份证号，格式必须正确（地区码+生日+顺序码+校验位）
- 护照号码可等同身份证
- 同一姓名多处记录，只要一处有完整信息即可
- 有直接合格情形（撤警、无效警情、无警情事实）

**不合格标准：**
- 身份证号缺失、不完整、格式错误，长度不正确（比如 17位  19位  16位  20位）
- 18位数字被标注为其他用途（手机号、工号等）

**业务口径明确：**
- ✅ **不检测校验位** - 校验位错误的18位身份证仍视为"有效格式"
- ✅ 只识别**长度错误**（不是15/18位）和**格式错误**（地区码、日期不合法）

### 1.2 测试数据分析（analysis-task-58-results-with-reasoning.xlsx）

**识别准确性：**
| 指标 | 数值 | 说明 |
|------|------|------|
| 准确率 | 83.3% | 5/6 正确 |
| 真阳性(TP) | 0 | 正确识别出错误的身份证号 |
| 假阴性(FN) | 1 | 实际是案件编号子串，不提取是**正确的** |
| 真阴性(TN) | 5 | 正确判断没有错误 |

**关键发现：**
- 模型**正确地**没有提取案件编号子串 `340422610000202512`（因为后面还有 `0115`）
- `NumberEvidenceExtractor` 的正则 `(?![\dXx])` 负向前瞻是正确的设计
- **缺少真正的独立无效身份证号测试用例**（如16位、17位、19位）

### 1.3 现有代码能力矩阵

| 组件 | 文件 | 能力 | 状态 |
|------|------|------|------|
| 证据提取器 | [NumberEvidenceExtractor.java](../backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java) | 提取并分类所有号码 | ✅ 完整 |
| 意图执行器 | [NumberIntentEvaluator.java](../backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java) | 配置化号码标签执行 | ✅ 完整 |
| 身份证提取器 | [IdCardExtractor.java](../backend/src/main/java/com/datalabeling/service/extraction/IdCardExtractor.java) | 传统提取方式 | ✅ 完整 |
| 内置标签初始化 | [BuiltinGlobalLabelsInitializer.java](../backend/src/main/java/com/datalabeling/service/BuiltinGlobalLabelsInitializer.java) | 系统启动时创建内置标签 | ⚠️ 未使用number_intent |

### 1.4 支持的号码类型

| 类型代码 | 说明 | 示例 | 是否计入"valid" | 是否计入"invalid" |
|----------|------|------|-----------------|------------------|
| `ID_VALID_18` | 18位身份证（结构匹配+校验位通过） | `110101199001011234` | ✅ | ❌ |
| `ID_VALID_15` | 15位旧身份证（结构匹配） | `110101900101123` | ✅（可配置） | ❌ |
| `ID_INVALID_LENGTH` | 长度错误（16/17/19/20位） | `11010119900101123`（17位） | ❌ | ✅ |
| `ID_INVALID_CHECKSUM` | 18位但校验位错误 | `340402199711180212` | ✅ | ❌（业务口径） |
| `ID_MASKED` | 遮挡身份证（18/15位） | `110101********1234` | ❌ | ❌ |
| `ID_INVALID_LENGTH_MASKED` | 遮挡且长度错误 | `110101********123`（17位） | ❌ | ✅ |

**关键决策：**
- `ID_INVALID_CHECKSUM` 不计入 `invalid`（保持 `idChecksumInvalidIsInvalid=false`）
- "有效身份证" = `ID_VALID_18` + `ID_VALID_15`（可选）+ `ID_INVALID_CHECKSUM`
- "无效身份证" = `ID_INVALID_LENGTH` + `ID_INVALID_LENGTH_MASKED`

---

## 二、代码逻辑深度分析

### 2.1 NumberEvidenceExtractor 核心流程

```java
// NumberEvidenceExtractor.java:70-144
public NumberEvidence extract(String text) {
    // 1) 候选抽取（保留位置信息）
    //    - ID_18_WITH_X: 末位X的18位（优先，避免被拆分）
    //    - ID_19_ENDING_X: 末位X的19位
    //    - DIGITS_14_22: 14-22位纯数字候选 (?<!\d)\d{14,22}(?![\dXx])
    //    - PHONE_11_START_1: 11位手机号
    //    - MASKED_16_19: 16-19位遮挡数字串

    // 2) 去重：同位置同值只保留一次

    // 3) 分类与证据构建
    for (CandidateMatch m : unique) {
        Classification c = classify(normalized, m, text);
        // ... 构建 NumberCandidate
    }

    // 4) 派生字段统计
    derive(evidence);
}
```

**关键正则解析：**

| 正则表达式 | 作用 | 关键点 |
|-----------|------|--------|
| `(?<!\d)\d{14,22}(?![\dXx])` | 提取14-22位数字 | `(?![\dXx])` 负向前瞻避免提取子串 |
| `(?<!\d)\d{17}[Xx](?!\d)` | 提取末位X的18位 | 优先执行，避免被纯数字规则拆分 |
| `(?<!\d)\d{18}[Xx](?!\d)` | 提取末位X的19位 | 多一位+X的常见错误 |
| `^[1-6]\d{5}(19\|20)\d{2}(0[1-9]\|1[0-2])(0[1-9]\|[12]\d\|3[01])\d{3}[0-9Xx]$` | 18位结构验证 | 地区码(1-6开头)+日期 |

### 2.2 分类逻辑详解

```java
// NumberEvidenceExtractor.java:178-305
private Classification classify(String number, CandidateMatch span, String fullText) {
    // 0) 遮挡号码（包含*）
    if (number.indexOf('*') >= 0) {
        if (isPhoneMasked(number)) return PHONE_MASKED;
        if (isBankCardMasked(number)) return BANK_CARD_MASKED;
        return classifyIdMasked(number, span, fullText);  // ID_MASKED 或 ID_INVALID_LENGTH_MASKED
    }

    // 1) 手机号（有效）
    if (isPhone(number)) return PHONE;

    // 2) 身份证有效18位
    if (number.length() == 18 && ID_18_STRUCTURE.matcher(number).matches()) {
        boolean checksumOk = validateIdCardCheckBit(number);
        // 注意：即使 checksumOk=false，也返回 ID_INVALID_CHECKSUM
        // 是否计入 "invalid" 由 NumberIntentEvaluator 的 policy 控制
        return Classification.of(checksumOk ? "ID_VALID_18" : "ID_INVALID_CHECKSUM", ...);
    }

    // 3) 身份证有效15位
    if (number.length() == 15 && ID_15_STRUCTURE.matcher(number).matches()) {
        return ID_VALID_15;
    }

    // 3.1) 19位末位X：明显的位数错误
    if (number.length() == 19 && ID_19_ENDING_X.matcher(number).matches()) {
        return ID_INVALID_LENGTH;
    }

    // 4) Near-Miss检测：删除一位可恢复为有效18位
    IdNearMiss nearMiss = findValidId18ByRemovingOneDigit(number);
    if (nearMiss != null && number.length() != 18) {
        return ID_INVALID_LENGTH;  // 附加 validation: id_near_miss_remove_one_digit
    }

    // 5) 错误身份证（位数不对，但结构特征强）
    boolean idLike = isIdLikeByAreaAndBirth(number);
    if (idLike && number.length() != 18) {
        return ID_INVALID_LENGTH;
    }

    // 6) 银行卡（Luhn校验）
    if (isBankCardByLuhn(number)) {
        return BANK_CARD;
    }

    return null;
}
```

### 2.3 isIdLikeByAreaAndBirth 结构判断

```java
// NumberEvidenceExtractor.java:656-692
private boolean isIdLikeByAreaAndBirth(String number) {
    if (number.length() < 14) return false;

    // 地区码首位：1-6（华北、东北、华东、中南、西南、西北）
    char first = number.charAt(0);
    if (first < '1' || first > '6') return false;

    // 检查是否为有效格式（纯数字 或 18位且末位为X）
    if (!isAllDigitsOrXEnding(number)) return false;

    // 生日段 YYYYMMDD 可解析（含闰年校验）
    String birth = number.substring(6, 14);
    int year = Integer.parseInt(birth.substring(0, 4));
    int month = Integer.parseInt(birth.substring(4, 6));
    int day = Integer.parseInt(birth.substring(6, 8));

    // 年份范围：1900 - 当前年份+1
    if (year < 1900 || year > currentYear + 1) return false;

    // 日期有效性校验（自动处理闰年、2月天数等）
    LocalDate.of(year, month, day);
    return true;
}
```

### 2.4 NumberIntentEvaluator 意图执行

```java
// NumberIntentEvaluator.java:35-144
public EvaluationResult evaluate(String text, NumberIntentConfig intent) {
    // 1) 提取证据
    NumberEvidence evidence = evidenceExtractor.extract(text);

    // 2) 按实体过滤
    List<NumberCandidate> candidates = filterByEntity(evidence.getNumbers(), "id_card");

    // 3) 统计（counts 需与 selection 口径一致）
    Counts counts = countByCategory(candidates, entity,
        idChecksumInvalidIsInvalid,  // = false（业务口径）
        id18XIsInvalid);              // = false

    // 4) 选择候选（根据 task 和 include）
    Selection selection = selectCandidates(task, includeSet, candidates, entity,
        requireKeywordForInvalidBank,
        idChecksumInvalidIsInvalid,   // = false
        id18XIsInvalid);              // = false

    // 5) 组装输出
    out.setSummary(...);      // "是"/"否" 或脱敏列表
    out.setReasoning(...);    // 详细推理过程
    out.setExtractedData(...); // 结构化证据
}
```

### 2.5 isInvalidType 判断逻辑（关键）

```java
// NumberIntentEvaluator.java:397-421
private static boolean isInvalidType(String entity, String type, NumberCandidate candidate,
                                     boolean idChecksumInvalidIsInvalid,  // = false
                                     boolean id18XIsInvalid) {             // = false
    if (!"id_card".equals(entity)) {
        return isInvalidType(entity, type);
    }

    // 1) 长度错误：始终计入 invalid
    if ("ID_INVALID_LENGTH".equals(type)) {
        return true;
    }

    // 2) 校验位错误：业务口径 = 不计入 invalid
    if (idChecksumInvalidIsInvalid && "ID_INVALID_CHECKSUM".equals(type)) {
        return true;  // 此分支不会执行（idChecksumInvalidIsInvalid=false）
    }

    // 3) 18位末位X：业务口径 = 不计入 invalid
    if (id18XIsInvalid && "ID_VALID_18".equals(type) && candidate != null) {
        char last = candidate.getValue().charAt(candidate.getValue().length() - 1);
        return last == 'X' || last == 'x';  // 此分支不会执行（id18XIsInvalid=false）
    }

    return false;
}
```

---

## 三、优化方案

### 3.1 方案概述

**核心思路：**
1. ✅ 保持 `idChecksumInvalidIsInvalid=false`（不检测校验位）
2. 使用 `number_intent` 配置内置标签，充分利用现有能力
3. 增强测试用例，覆盖各种长度错误场景
4. 优化多轮对话提示词，引导小模型依赖规则证据

### 3.2 明确业务口径

**"错误的身份证号"定义（业务口径）：**

| 类型 | 定义 | 示例 | 代码类型 |
|------|------|------|----------|
| 长度错误 | 位数不是15或18 | `11010119900101123`（17位） | `ID_INVALID_LENGTH` |
| 遮挡+长度错误 | 遮挡且位数不对 | `110101********123`（17位） | `ID_INVALID_LENGTH_MASKED` |
| 格式错误 | 地区码/日期不合法 | `000000199001011234`（地区码0开头） | 不提取（非身份证特征） |

**不计入"错误"的类型：**

| 类型 | 说明 | 处理方式 |
|------|------|----------|
| 校验位错误 | 18位但校验位不通过 | 按 `ID_VALID_18` 处理（不计入invalid） |
| 末位X | 18位末位为X | 按 `ID_VALID_18` 处理（不计入invalid） |
| 案件编号子串 | 长数字串的一部分 | 不提取（负向前瞻规则） |

### 3.3 内置标签配置优化

#### 修改 BuiltinGlobalLabelsInitializer.java

```java
// 在 buildPolicePersonnelPreprocessorConfigJson() 中添加
private String buildPolicePersonnelPreprocessorConfigJson() {
    Map<String, Object> cfg = new HashMap<>();

    // 使用 number_intent 配置（推荐）
    Map<String, Object> numberIntent = new HashMap<>();
    numberIntent.put("entity", "id_card");
    numberIntent.put("task", "exists");  // 任务：检查是否存在身份证号

    Map<String, Object> policy = new HashMap<>();
    policy.put("idChecksumInvalidIsInvalid", false);  // 业务口径：不检测校验位
    policy.put("id15IsValid", true);                   // 15位旧身份证视为有效
    policy.put("id18XIsInvalid", false);               // 末位X视为有效
    policy.put("defaultMaskedOutput", true);           // 默认输出脱敏值
    numberIntent.put("policy", policy);

    Map<String, Object> output = new HashMap<>();
    output.put("format", "list");
    output.put("maxItems", 50);
    output.put("joiner", "，");
    numberIntent.put("output", output);

    cfg.put("number_intent", numberIntent);

    // 保留传统提取器作为补充（护照、关键词）
    cfg.put("extractors", Arrays.asList("passport", "keyword_match"));

    // ... 其他配置

    return toJson(cfg);
}
```

**优点：**
- ✅ 充分利用现有的 `NumberIntentEvaluator` 能力
- ✅ 自动包含详细的推理和证据统计
- ✅ 灵活配置，易于调整业务口径
- ✅ 与内置的二次强化机制无缝集成

### 3.4 配置对比

| 配置项 | 传统提取器 | number_intent | 说明 |
|--------|-----------|---------------|------|
| 提取能力 | `IdCardExtractor` | `NumberEvidenceExtractor` | 后者更强大 |
| 分类能力 | 简单（valid/invalid） | 详细（6种类型） | 后者更精细 |
| 推理输出 | 简单文本 | 结构化证据 | 后者更可审计 |
| 灵活性 | 需要修改代码 | JSON配置 | 后者更灵活 |
| 与强化集成 | 需要额外处理 | 自动集成 | 后者更便捷 |

---

## 四、边界情况与测试用例

### 4.1 边界情况矩阵

| 场景 | 输入示例 | 预期类型 | 预期是否计入invalid | 说明 |
|------|----------|----------|---------------------|------|
| 标准18位 | `110101199001011234` | `ID_VALID_18` | ❌ | 有效身份证 |
| 18位+末位X | `11010119900101123X` | `ID_VALID_18` | ❌ | X是合法校验位 |
| 校验位错误 | `340402199711180212` | `ID_INVALID_CHECKSUM` | ❌ | 业务口径不计入invalid |
| 17位（少1位） | `11010119900101123` | `ID_INVALID_LENGTH` | ✅ | 典型长度错误 |
| 16位（少2位） | `1101011990010112` | `ID_INVALID_LENGTH` | ✅ | 典型长度错误 |
| 19位+X | `110101199001011234X` | `ID_INVALID_LENGTH` | ✅ | 19位末位X |
| 19位纯数字 | `1101011990010112345` | `ID_INVALID_LENGTH` | ✅ | near-miss可恢复 |
| 15位旧身份证 | `110101900101123` | `ID_VALID_15` | ❌ | 可配置是否有效 |
| 遮挡18位 | `110101********1234` | `ID_MASKED` | ❌ | 遮挡不计入invalid |
| 遮挡17位 | `110101********123` | `ID_INVALID_LENGTH_MASKED` | ✅ | 遮挡+长度错误 |
| 案件编号子串 | `A3404226100002025120115` | 不提取 | - | 负向前瞻避免 |
| 手机号 | `13812345678` | `PHONE` | - | 被排除 |
| 银行卡号 | `6227001234567890` | `BANK_CARD` | - | 被排除 |
| 地区码0开头 | `000000199001011234` | 不提取 | - | 地区码不合法 |
| 日期不合法 | `110101199002301234` | 不提取 | - | 2月30日不存在 |
| 顺序码相同 | `110101199001011111` | `ID_INVALID_CHECKSUM` | ❌ | 结构合法，校验位错 |

### 4.2 单元测试用例

#### 测试文件：NumberEvidenceExtractorIdCardTest.java

```java
package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberEvidenceExtractorIdCardTest {

    private final NumberEvidenceExtractor extractor = new NumberEvidenceExtractor();

    @Test
    void test标准18位身份证() {
        String text = "身份证号：110101199001011234";
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        NumberEvidence.NumberCandidate candidate = evidence.getNumbers().get(0);
        assertEquals("ID_VALID_18", candidate.getType());
        assertEquals("110101199001011234", candidate.getValue());
        assertTrue(candidate.getValidations().stream()
            .anyMatch(v -> "id_checksum".equals(v.getName()) && v.isPass()));
    }

    @Test
    void test18位末位X() {
        String text = "身份证号：11010119900101123X";
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("ID_VALID_18", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test校验位错误_不计入invalid() {
        String text = "身份证号：340402199711180212";  // 校验位错误
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        NumberEvidence.NumberCandidate candidate = evidence.getNumbers().get(0);
        assertEquals("ID_INVALID_CHECKSUM", candidate.getType());

        // 验证：在 idChecksumInvalidIsInvalid=false 时，不计入 invalid
        boolean isInvalid = NumberIntentEvaluator.isInvalidType(
            "id_card", "ID_INVALID_CHECKSUM", candidate, false, false);
        assertFalse(isInvalid, "校验位错误不应计入invalid（业务口径）");
    }

    @Test
    void test17位长度错误() {
        String text = "身份证号：11010119900101123";  // 17位
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        NumberEvidence.NumberCandidate candidate = evidence.getNumbers().get(0);
        assertEquals("ID_INVALID_LENGTH", candidate.getType());
        assertTrue(candidate.getValidations().stream()
            .anyMatch(v -> "id_like".equals(v.getName())));

        // 验证：长度错误计入 invalid
        boolean isInvalid = NumberIntentEvaluator.isInvalidType(
            "id_card", "ID_INVALID_LENGTH", candidate, false, false);
        assertTrue(isInvalid, "长度错误应计入invalid");
    }

    @Test
    void test16位长度错误() {
        String text = "身份证号：1101011990010112";  // 16位
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("ID_INVALID_LENGTH", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test19位末位X() {
        String text = "身份证号：110101199001011234X";  // 19位+X
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        NumberEvidence.NumberCandidate candidate = evidence.getNumbers().get(0);
        assertEquals("ID_INVALID_LENGTH", candidate.getType());
        assertTrue(candidate.getValidations().stream()
            .anyMatch(v -> "id_suffix_x".equals(v.getName())));
    }

    @Test
    void test19位纯数字_nearMiss() {
        String text = "身份证号：1101011990010112345";  // 19位
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        NumberEvidence.NumberCandidate candidate = evidence.getNumbers().get(0);
        assertEquals("ID_INVALID_LENGTH", candidate.getType());
        assertTrue(candidate.getValidations().stream()
            .anyMatch(v -> "id_near_miss_remove_one_digit".equals(v.getName())));
    }

    @Test
    void test案件编号子串不被提取() {
        String text = "案件编号：A3404226100002025120115";
        NumberEvidence evidence = extractor.extract(text);

        // 不应该提取出任何身份证号（因为是长数字串的子串）
        boolean hasIdCard = evidence.getNumbers().stream()
            .anyMatch(n -> n.getType().startsWith("ID_"));
        assertFalse(hasIdCard, "案件编号子串不应被提取为身份证号");
    }

    @Test
    void test遮挡18位() {
        String text = "身份证号：110101********1234";
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("ID_MASKED", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test遮挡17位_长度错误() {
        String text = "身份证号：110101********123";  // 17位
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("ID_INVALID_LENGTH_MASKED", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test手机号不被误判() {
        String text = "联系电话：13812345678";
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("PHONE", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test地区码0开头不提取() {
        String text = "身份证号：000000199001011234";  // 地区码0开头
        NumberEvidence evidence = extractor.extract(text);

        // 地区码不合法，不会被提取为身份证
        boolean hasIdCard = evidence.getNumbers().stream()
            .anyMatch(n -> n.getType().startsWith("ID_"));
        assertFalse(hasIdCard, "地区码0开头不应被提取为身份证号");
    }

    @Test
    void test日期不合法不提取() {
        String text = "身份证号：110101199002301234";  // 2月30日不存在
        NumberEvidence evidence = extractor.extract(text);

        // 日期不合法，不会被提取为身份证
        boolean hasIdCard = evidence.getNumbers().stream()
            .anyMatch(n -> n.getType().startsWith("ID_"));
        assertFalse(hasIdCard, "日期不合法不应被提取为身份证号");
    }

    @Test
    void test15位旧身份证() {
        String text = "身份证号：110101900101123";  // 15位
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(1, evidence.getNumbers().size());
        assertEquals("ID_VALID_15", evidence.getNumbers().get(0).getType());
    }

    @Test
    void test多个身份证号混合() {
        String text = "身份证1：110101199001011234，身份证2：31010119900101123（17位错误）";
        NumberEvidence evidence = extractor.extract(text);

        assertEquals(2, evidence.getNumbers().size());

        // 验证统计
        Map<String, Object> derived = evidence.getDerived();
        assertEquals(1, derived.get("id_valid_18_count"));
        assertEquals(1, derived.get("id_invalid_length_count"));
        assertTrue((Boolean) derived.get("id_exists"));
    }
}
```

### 4.3 集成测试用例

#### 测试文件：NumberIntentEvaluatorIntegrationTest.java

```java
package com.datalabeling.service.extraction;

import com.datalabeling.dto.NumberIntentConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberIntentEvaluatorIntegrationTest {

    private final NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

    @Test
    void testExists任务_有有效身份证() {
        String text = "身份证号：110101199001011234";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("exists")
            .policy(NumberIntentConfig.Policy.builder()
                .idChecksumInvalidIsInvalid(false)
                .build())
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertTrue(result.isCanHandle());
        assertTrue(result.isHit());
        assertEquals("是", result.getSummary());
        assertTrue(result.getReasoning().contains("valid=1"));
    }

    @Test
    void testExists任务_只有长度错误() {
        String text = "身份证号：11010119900101123";  // 17位
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("exists")
            .policy(NumberIntentConfig.Policy.builder()
                .idChecksumInvalidIsInvalid(false)
                .build())
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertTrue(result.isHit());
        assertEquals("是", result.getSummary());
        assertTrue(result.getReasoning().contains("invalid=1"));
    }

    @Test
    void testInvalid任务_只提取长度错误() {
        String text = "身份证1：110101199001011234（有效），身份证2：11010119900101123（17位错误）";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("invalid")
            .include(Arrays.asList("invalid"))
            .policy(NumberIntentConfig.Policy.builder()
                .idChecksumInvalidIsInvalid(false)
                .build())
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertTrue(result.isHit());
        // 只应提取17位长度错误
        assertEquals(1, ((List<?>) result.getExtractedData().get("items")).size());
        assertTrue(result.getReasoning().contains("110***********1123"));  // 脱敏后
    }

    @Test
    void testInvalid任务_校验位错误不计入() {
        String text = "身份证号：340402199711180212";  // 校验位错误
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("invalid")
            .include(Arrays.asList("invalid"))
            .policy(NumberIntentConfig.Policy.builder()
                .idChecksumInvalidIsInvalid(false)  // 业务口径
                .build())
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertFalse(result.isHit(), "校验位错误不应计入invalid");
        assertEquals("无", result.getSummary());
    }

    @Test
    void testExtract任务_提取所有类型() {
        String text = "有效：110101199001011234，错误：11010119900101123，遮挡：110101********1234";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("extract")
            .include(Arrays.asList("valid", "invalid", "masked"))
            .output(NumberIntentConfig.Output.builder()
                .format("text")
                .joiner("；")
                .build())
            .policy(NumberIntentConfig.Policy.builder()
                .defaultMaskedOutput(true)
                .build())
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertTrue(result.isHit());
        assertEquals(3, ((List<?>) result.getExtractedData().get("items")).size());
        assertTrue(result.getSummary().contains("；"));
    }

    @Test
    void test涉警当事人场景_直接合格() {
        String text = "报警人称误报警，请求撤警，不需要处理。";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("exists")
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertFalse(result.isHit(), "撤警情形不应有身份证号");
        assertEquals("否", result.getSummary());
    }

    @Test
    void test涉警当事人场景_有完整身份证() {
        String text = "报警人张三，身份证号110101199001011234，报警人称李四打人。";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("exists")
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        assertTrue(result.isHit());
        assertEquals("是", result.getSummary());
    }

    @Test
    void test涉警当事人场景_身份证17位错误() {
        String text = "报警人张三，身份证号11010119900101123（17位），报警人称李四打人。";
        NumberIntentConfig config = NumberIntentConfig.builder()
            .entity("id_card")
            .task("exists")
            .build();

        NumberIntentEvaluator.EvaluationResult result = evaluator.evaluate(text, config);

        // exists 任务认为有身份证号（虽然是错误的）
        assertTrue(result.isHit());
        assertTrue(result.getReasoning().contains("invalid=1"));
    }
}
```

---

## 五、小模型配合最佳实践

### 5.1 小模型幻觉问题分析

**典型幻觉表现：**

| 类型 | 示例 | 原因 |
|------|------|------|
| 凭空创造 | 文本中无身份证号，模型却说"有" | 训练数据偏差 |
| 格式误判 | 把手机号当作身份证号 | 上下文理解不足 |
| 长度不敏感 | 17位身份证当作"有效" | 数字序列理解弱 |
| 误读遮挡 | `110101********1234` 当作"缺失" | 遮挡符号理解偏差 |

**解决策略：代码逻辑 > 模型判断**

### 5.2 多轮对话架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        数据处理流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  输入文本                                                        │
│     │                                                            │
│     ▼                                                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  第一轮：规则证据提取（NumberEvidenceExtractor）           │   │
│  │  - 确定性正则匹配                                          │   │
│  │  - 结构验证（地区码、日期、校验位）                         │   │
│  │  - Near-Miss检测                                          │   │
│  │  输出：NumberEvidence（结构化证据）                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│     │                                                            │
│     ▼                                                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  第二轮：意图执行（NumberIntentEvaluator）                 │   │
│  │  - 配置化的分类统计                                         │   │
│  │  - 生成推理文本                                            │   │
│  │  - 组装输出（是/否 + 脱敏列表 + 证据）                      │   │
│  │  输出：EvaluationResult（初始判断）                         │   │
│  └──────────────────────────────────────────────────────────┘   │
│     │                                                            │
│     ├──────────────────────────────────────────────────────┐   │
│     │ 置信度 >= 75？                                          │   │
│     ├──────────────────────────────────────────────────────┤   │
│     │ YES  │  NO                                             │   │
│     ▼      ▼                                                 │   │
│  ┌─────────┐  ┌─────────────────────────────────────────┐   │
│  │  直接   │  │  第三轮：二次强化（LLM）                   │   │
│  │  返回   │  │  - 基于规则证据进行验证                   │   │
│  │  结果   │  │  - 人工审核级别的判断                     │   │
│  └─────────┘  │  - 修正低置信度判断                       │   │
│               └─────────────────────────────────────────┘   │
│                        │                                        │
│                        ▼                                        │
│                   最终结果                                       │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 二次强化提示词模板

```handlebars
你是数据质量审核专家，请对以下"涉警当事人信息完整性检查"的初步结果进行二次验证。

=== 任务信息 ===
标签名称：{{label_name}}
标签描述：{{label_description}}

=== 原始数据 ===
{{row_data_json}}

=== 初步分析结果 ===
判断：{{initial_result}}
置信度：{{initial_confidence}}%
推理：{{initial_reasoning}}

{{#if validation_result}}
=== 规则验证结果（代码逻辑提取）===
{{validation_result}}
{{/if}}

=== 二次审核重点 ===
1. 豁免情形判断是否正确？
   - 撤警、无效警情、无警情事实等关键词是否命中？
   - 命中后是否正确判定为"直接合格"？

2. 涉警当事人识别是否准确？
   - 是否误纳入民警、辅警、围观群众？
   - 是否遗漏实际当事人？

3. 身份证/护照号验证是否充分？
   - 代码规则已提取：{{extracted_count}}个号码
   - 有效：{{valid_count}}个
   - 长度错误：{{invalid_count}}个
   - 遮挡：{{masked_count}}个

4. 置信度评估是否合理？
   - 低置信度（<75%）可能需要人工复核

=== 重要提示 ===
- 代码规则使用确定性逻辑，提取结果可信度高
- 请优先依赖规则提取的证据，不要凭空臆测
- 如果规则提取的证据与初步判断不一致，请仔细核对

=== 输出要求 ===
输出JSON格式：
{
  "final_result": "维持原判"或"修正为是/否",
  "final_confidence": 0-100,
  "validation_notes": "二次审核发现的问题或确认的理由",
  "should_adjust": true/false,
  "adjustment_reason": "如果需要修正，说明原因"
}
```

### 5.4 验证结果格式化

需要在代码中将 `NumberEvidence` 转换为易读的文本：

```java
// 在 AnalysisTaskAsyncService.java 中添加
private String formatValidationResult(NumberEvidence evidence) {
    if (evidence == null || evidence.getNumbers().isEmpty()) {
        return "未提取到身份证号";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== 身份证号提取证据 ===\n");

    Map<String, Object> derived = evidence.getDerived();
    sb.append("统计：\n");
    sb.append("  - 有效18位：").append(derived.get("id_valid_18_count")).append("个\n");
    sb.append("  - 有效15位：").append(derived.get("id_valid_15_count")).append("个\n");
    sb.append("  - 长度错误：").append(derived.get("id_invalid_length_count")).append("个\n");
    sb.append("  - 校验位错误：").append(derived.get("id_invalid_checksum_count")).append("个（不计入无效）\n");
    sb.append("  - 遮挡：").append(derived.get("id_masked_count")).append("个\n");
    sb.append("\n");

    sb.append("详细列表：\n");
    for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
        if (!n.getType().startsWith("ID_")) continue;

        sb.append("  ").append(n.getId()).append(". ");
        sb.append("[").append(n.getType()).append("] ");
        sb.append(n.getMaskedValue()).append(" (");
        sb.append(n.getLength()).append("位)");

        if (n.getType().equals("ID_INVALID_LENGTH")) {
            sb.append(" 【长度错误】");
        } else if (n.getType().equals("ID_INVALID_CHECKSUM")) {
            sb.append(" 【校验位错误，但仍视为有效格式】");
        } else if (n.getType().equals("ID_MASKED")) {
            sb.append(" 【遮挡】");
        }

        // 显示关键词提示
        if (n.getKeywordHint() != null && !n.getKeywordHint().isEmpty()) {
            sb.append("（命中关键词：").append(n.getKeywordHint()).append("）");
        }

        sb.append("\n");

        // 显示验证详情
        for (NumberEvidence.ValidationItem v : n.getValidations()) {
            sb.append("      - ").append(v.getName()).append(": ");
            sb.append(v.isPass() ? "✓" : "✗");
            sb.append(" ").append(v.getDetail()).append("\n");
        }
    }

    return sb.toString();
}
```

### 5.5 提示词优化技巧

| 技巧 | 说明 | 示例 |
|------|------|------|
| **证据先行** | 先展示规则提取的证据 | "代码规则已提取：3个号码" |
| **数字量化** | 使用具体数字而非描述 | "有效：2个，无效：1个" |
| **明确规则** | 说明代码规则的判断逻辑 | "18位但校验位错误仍视为有效格式" |
| **对比分析** | 对比初始判断与证据 | "初始判断：否，证据：有1个有效身份证" |
| **引导思考** | 提出审核要点而非答案 | "请核对：是否误纳入民警？" |
| **格式约束** | 要求结构化输出 | "输出JSON格式" |

---

## 六、实施计划

### 阶段1：代码优化（1天）

**任务清单：**

- [ ] 修改 `BuiltinGlobalLabelsInitializer.java`
  - [ ] 为"涉警当事人信息完整性检查"添加 `number_intent` 配置
  - [ ] 为"在校学生信息完整性检查"添加 `number_intent` 配置
  - [ ] 设置 `idChecksumInvalidIsInvalid=false`
  - [ ] 设置 `id15IsValid=true`（涉警）/ `false`（学生）

- [ ] 添加验证结果格式化方法
  - [ ] 在 `AnalysisTaskAsyncService` 中添加 `formatValidationResult()`
  - [ ] 在提示词中引用验证结果

**验收标准：**
- 内置标签使用 `number_intent` 配置
- 校验位错误不计入 `invalid`
- 验证结果可读性强

### 阶段2：单元测试（1天）

**任务清单：**

- [ ] 创建 `NumberEvidenceExtractorIdCardTest.java`
  - [ ] 测试标准18位身份证
  - [ ] 测试18位末位X
  - [ ] 测试校验位错误（不计入invalid）
  - [ ] 测试17位、16位长度错误
  - [ ] 测试19位末位X
  - [ ] 测试19位纯数字near-miss
  - [ ] 测试案件编号子串不被提取
  - [ ] 测试遮挡身份证
  - [ ] 测试手机号不被误判
  - [ ] 测试地区码/日期不合法不提取
  - [ ] 测试15位旧身份证
  - [ ] 测试多个身份证号混合

- [ ] 创建 `NumberIntentEvaluatorIntegrationTest.java`
  - [ ] 测试 `exists` 任务
  - [ ] 测试 `invalid` 任务
  - [ ] 测试 `extract` 任务
  - [ ] 测试校验位错误不计入invalid
  - [ ] 测试涉警当事人场景

**验收标准：**
- 所有测试通过
- 覆盖率 > 80%

### 阶段3：提示词优化（0.5天）

**任务清单：**

- [ ] 更新二次强化提示词模板
  - [ ] 添加规则证据展示
  - [ ] 添加审核要点列表
  - [ ] 添加重要提示

- [ ] 测试提示词效果
  - [ ] 使用测试数据验证
  - [ ] 对比优化前后准确率

**验收标准：**
- 提示词清晰易懂
- 模型依赖规则证据

### 阶段4：集成测试（0.5天）

**任务清单：**

- [ ] 使用 `analysis-task-58-results-with-reasoning.xlsx` 验证
- [ ] 补充测试用例（长度错误场景）
- [ ] 调整置信度阈值（如需要）
- [ ] 性能测试（如需要）

**验收标准：**
- 准确率 >= 90%（在现有测试数据上）
- 处理时间 < 2秒/条

---

## 七、配置参考手册

### 7.1 number_intent 配置结构

```json
{
  "number_intent": {
    "entity": "id_card",           // 实体：id_card / phone / bank_card
    "task": "exists",              // 任务：exists / extract / invalid / masked / invalid_length_masked
    "include": ["valid", "invalid"], // 提取类别（extract任务生效）
    "output": {
      "format": "list",            // 输出格式：list / text
      "maxItems": 50,              // 最大输出条数
      "joiner": "，"                // 拼接符
    },
    "policy": {
      "id15IsValid": true,         // 15位身份证是否有效
      "idChecksumInvalidIsInvalid": false, // 校验位错误是否计入invalid
      "id18XIsInvalid": false,     // 18位末位X是否计入invalid
      "defaultMaskedOutput": true, // 默认输出脱敏值
      "requireKeywordForInvalidBank": true // 无效银行卡是否需要关键词窗
    }
  }
}
```

### 7.2 常用配置模板

#### 模板1：检查身份证号是否存在

```json
{
  "number_intent": {
    "entity": "id_card",
    "task": "exists",
    "policy": {
      "id15IsValid": true,
      "idChecksumInvalidIsInvalid": false,
      "id18XIsInvalid": false
    }
  }
}
```

**输出：**
- 有身份证号：`"是"`
- 无身份证号：`"否"`

#### 模板2：提取所有身份证号

```json
{
  "number_intent": {
    "entity": "id_card",
    "task": "extract",
    "include": ["valid", "invalid", "masked"],
    "output": {
      "format": "text",
      "joiner": "；"
    },
    "policy": {
      "defaultMaskedOutput": true
    }
  }
}
```

**输出：**
`"110***********1234；310***********5123（长度错误）"`

#### 模板3：只提取错误的身份证号

```json
{
  "number_intent": {
    "entity": "id_card",
    "task": "invalid",
    "include": ["invalid"],
    "output": {
      "format": "list",
      "maxItems": 10
    },
    "policy": {
      "idChecksumInvalidIsInvalid": false
    }
  }
}
```

**输出：**
```json
{
  "items": [
    {"value": "110***********1123", "type": "ID_INVALID_LENGTH", "confidence": 85}
  ]
}
```

#### 模板4：只提取遮挡的错误身份证号

```json
{
  "number_intent": {
    "entity": "id_card",
    "task": "invalid_length_masked",
    "include": ["masked"],
    "policy": {
      "defaultMaskedOutput": true
    }
  }
}
```

**用途：**
- 检测数据录入中的遮挡+长度错误（如 `110101********123`）
- 用于数据质量监控

### 7.3 内置标签完整配置

#### 涉警当事人信息完整性检查

```java
private String buildPolicePersonnelPreprocessorConfigJson() {
    Map<String, Object> cfg = new HashMap<>();

    // 核心配置：使用 number_intent
    Map<String, Object> numberIntent = new HashMap<>();
    numberIntent.put("entity", "id_card");
    numberIntent.put("task", "exists");

    Map<String, Object> policy = new HashMap<>();
    policy.put("idChecksumInvalidIsInvalid", false);  // 业务口径
    policy.put("id15IsValid", true);                   // 15位有效
    policy.put("id18XIsInvalid", false);
    policy.put("defaultMaskedOutput", true);
    numberIntent.put("policy", policy);

    cfg.put("number_intent", numberIntent);

    // 补充提取器：护照、关键词
    cfg.put("extractors", Arrays.asList("passport", "keyword_match"));

    Map<String, Object> passportOpt = new HashMap<>();
    passportOpt.put("include_cn_only", false);

    Map<String, Object> kwOpt = new HashMap<>();
    kwOpt.put("keywords", Arrays.asList(
        "请求撤警", "误报警", "不需要处理", "已协商解决",
        "重复警情", "副单", "无效警情", "无效报警",
        "未发现报警情况", "现场无异常", "无警情发生"
    ));
    kwOpt.put("matchType", "any");

    Map<String, Map<String, Object>> extractorOptions = new HashMap<>();
    extractorOptions.put("passport", passportOpt);
    extractorOptions.put("keyword_match", kwOpt);
    cfg.put("extractorOptions", extractorOptions);

    return toJson(cfg);
}
```

#### 在校学生信息完整性检查

```java
private String buildStudentPreprocessorConfigJson() {
    Map<String, Object> cfg = new HashMap<>();

    // 核心配置：使用 number_intent
    Map<String, Object> numberIntent = new HashMap<>();
    numberIntent.put("entity", "id_card");
    numberIntent.put("task", "exists");

    Map<String, Object> policy = new HashMap<>();
    policy.put("idChecksumInvalidIsInvalid", false);
    policy.put("id15IsValid", false);  // 在校学生应该是18位新身份证
    policy.put("id18XIsInvalid", false);
    policy.put("defaultMaskedOutput", true);
    numberIntent.put("policy", policy);

    cfg.put("number_intent", numberIntent);

    // 补充提取器：学校信息、关键词
    cfg.put("extractors", Arrays.asList("school_info", "keyword_match"));

    Map<String, Object> schoolOpt = new HashMap<>();
    schoolOpt.put("exclude_training", true);

    Map<String, Object> kwOpt = new HashMap<>();
    kwOpt.put("keywords", Arrays.asList(
        "在校学生", "学生", "学号", "年级",
        "幼儿园", "小学", "中学", "初中", "高中", "中职", "技校", "学院", "大学"
    ));
    kwOpt.put("matchType", "any");

    Map<String, Map<String, Object>> extractorOptions = new HashMap<>();
    extractorOptions.put("school_info", schoolOpt);
    extractorOptions.put("keyword_match", kwOpt);
    cfg.put("extractorOptions", extractorOptions);

    return toJson(cfg);
}
```

### 7.4 二次强化配置

```json
{
  "triggerConfidence": 75,    // 置信度 < 75 时触发强化
  "promptId": 123             // 二次强化提示词ID
}
```

**在 BuiltinGlobalLabelsInitializer 中配置：**

```java
ensureBuiltinLabel(
    adminId,
    "涉警当事人信息完整性检查",
    buildPolicePersonnelLabelDescription(),
    buildPolicePersonnelPreprocessorConfigJson(),
    buildEnhancementConfigJson(75, policeEnhancementPromptId),  // 置信度阈值
    "person_info_integrity"
);
```

---

## 八、常见问题FAQ

### Q1: 为什么不检测校验位？

**A:** 业务口径决定。根据检测规则文档和业务需求：
- 校验位错误通常不影响身份证号的基本识别
- 18位且结构合法（地区码、日期）的号码即可使用
- 校验位更多用于校验录入是否正确，而非判断"是否存在身份证号"

### Q2: 如何判断一个号码是"错误的身份证号"？

**A:** 按照当前业务口径：
- **长度错误**：不是15位或18位（如16位、17位、19位）
- **格式错误**：地区码/日期不合法（如0开头、2月30日）
- **不计入错误**：18位但校验位错误、末位X

### Q3: 案件编号会被误判为身份证号吗？

**A:** 不会。代码使用了负向前瞻规则：
```regex
(?<!\d)\d{14,22}(?![\dXx])
```
`(?![\dXx])` 确保数字后面**没有其他数字或X**，因此不会提取案件编号的子串。

### Q4: 如何处理遮挡的身份证号？

**A:** 遮挡号码分为两类：
- `ID_MASKED`：18位或15位遮挡，不计入invalid
- `ID_INVALID_LENGTH_MASKED`：遮挡且位数不对，计入invalid

业务口径：全遮挡（全*）不计存在。

### Q5: 小模型幻觉严重怎么办？

**A:** 多层防护：
1. **规则优先**：`NumberEvidenceExtractor` 使用确定性逻辑
2. **证据展示**：在提示词中展示规则提取的证据
3. **二次强化**：低置信度时触发LLM二次验证
4. **置信度阈值**：调整触发强化的阈值

### Q6: 如何调整置信度阈值？

**A:** 在 `EnhancementConfig` 中配置：
```json
{
  "triggerConfidence": 75  // 置信度 < 75 时触发强化
}
```
- 提高阈值（如85）：减少强化次数，提高性能
- 降低阈值（如60）：增加强化次数，提高准确率

### Q7: 如何验证配置是否生效？

**A:** 检查以下内容：
1. 日志中是否有 `号码意图规则执行（number_intent）` 字样
2. `reasoning` 中是否包含详细的证据统计
3. 测试用例中校验位错误是否不计入invalid

### Q8: 性能是否受影响？

**A:** 影响很小：
- `NumberEvidenceExtractor` 使用正则，速度快
- Near-miss检测仅对19位数字执行
- 建议对10万+数据量进行性能测试

### Q9: 如何处理特殊格式（如护照）？

**A:** 使用 `extractors` 配置补充提取器：
```json
{
  "extractors": ["passport", "keyword_match"]
}
```
护照号码等同于身份证号，可满足"信息完整"要求。

### Q10: 如何回滚到传统提取器？

**A:** 移除 `number_intent` 配置，保留 `extractors` 配置即可：
```json
{
  "extractors": ["id_card", "passport", "keyword_match"],
  "extractorOptions": {
    "id_card": {
      "include18Digit": true,
      "include15Digit": false,
      "includeLoose": false
    }
  }
}
```

---

## 九、总结

### 核心要点

1. **业务口径明确**：不检测校验位，只识别长度错误和格式错误
2. **现有代码完善**：`NumberEvidenceExtractor` 和 `NumberIntentEvaluator` 已具备所有需要的能力
3. **配置驱动**：通过 `number_intent` 配置即可启用，无需修改核心代码
4. **小模型配合**：规则证据优先 + 二次强化，显著提升准确度

### 实施路径

```
阶段1：配置内置标签（0.5天）
   ↓
阶段2：添加单元测试（1天）
   ↓
阶段3：优化提示词（0.5天）
   ↓
阶段4：集成测试验证（0.5天）
```

### 预期效果

| 指标 | 当前 | 优化后 | 提升 |
|------|------|--------|------|
| 准确率 | 83.3% | >= 90% | +7% |
| 误报率 | 0% | 0% | 保持 |
| 可解释性 | 中 | 高 | 显著提升 |
| 维护成本 | 中 | 低 | 配置驱动 |

---

## 附录：相关文件清单

| 文件 | 作用 | 关键行 |
|------|------|--------|
| [NumberEvidenceExtractor.java](../backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java) | 核心证据提取器 | 全文 |
| [NumberIntentEvaluator.java](../backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java) | 意图执行器 | L35-L144 |
| [BuiltinGlobalLabelsInitializer.java](../backend/src/main/java/com/datalabeling/service/BuiltinGlobalLabelsInitializer.java) | 内置标签配置 | L197-L252 |
| [NumberIntentConfig.java](../backend/src/main/java/com/datalabeling/dto/NumberIntentConfig.java) | 配置DTO | L104-L107 |
| [AnalysisTaskAsyncService.java](../backend/src/main/java/com/datalabeling/service/AnalysisTaskAsyncService.java) | 异步任务执行 | 需添加格式化方法 |

---

**文档版本**: v1.0
**更新日期**: 2026-01-27
**作者**: DataLabeling System Team
