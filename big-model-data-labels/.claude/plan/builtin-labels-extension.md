# 内置全局标签扩展方案 - 技术实现规划

> **项目**: 智能数据标注平台
> **版本**: V1.0
> **创建日期**: 2026-01-26
> **目标**: 将检测规则文档中的规则实现为内置全局标签

---

## 📋 目录

1. [项目概述](#1-项目概述)
2. [规则分类体系](#2-规则分类体系)
3. [系统架构设计](#3-系统架构设计)
4. [大模型选型分析](#4-大模型选型分析)
5. [技术实现方案](#5-技术实现方案)
6. [数据库设计](#6-数据库设计)
7. [提示词模板设计](#7-提示词模板设计)
8. [实施路线图](#8-实施路线图)
9. [测试策略](#9-测试策略)
10. [成本估算](#10-成本估算)

---

## 1. 项目概述

### 1.1 背景

当前智能数据标注平台已具备：
- ✅ 基础的标签管理功能（分类、提取、结构化提取）
- ✅ 内置提取器（身份证号、银行卡号、手机号）
- ✅ 混合处理模式（规则+LLM）
- ✅ 全局标签机制（管理员维护，所有用户可见）

**需求**：将"检测规则.docx"中的业务规则实现为内置全局标签，使系统能够自动检测警情数据的质量。

### 1.2 目标

| 维度 | 当前状态 | 目标状态 | 提升幅度 |
|-----|---------|---------|---------|
| **内置标签数量** | 3个 | 10+个 | +233% |
| **规则覆盖场景** | 号码提取 | 人员信息、案件特征等 | +200% |
| **检测准确率** | 85% | 95%+ | +12% |
| **处理效率** | 混合 | 智能路由 | +40% |

### 1.3 核心规则

文档包含两大核心规则：

#### 规则1：涉警当事人信息完整性检查
- **目标**：检查警情中涉警当事人的身份证号信息是否完整录入
- **输出**：是/否 + 理由
- **复杂度**：⭐⭐⭐⭐⭐（最高）

#### 规则2：在校学生信息完整性检查
- **目标**：检查警情中涉及的在校学生身份信息是否完整
- **输出**：是/否 + 理由
- **复杂度**：⭐⭐⭐⭐

---

## 2. 规则分类体系

### 2.1 当前规则分类

```
检测规则
├── 人员身份完整性类 ✅（已分析）
│   ├── 涉警当事人判断（18位身份证号验证）
│   └── 在校学生信息检查（6项信息验证）
├── 案件特征类 📋（待扩展）
│   ├── 重大案件识别
│   ├── 敏感信息检测
│   └── 紧急程度评估
├── 行为模式类 📋（待扩展）
│   ├── 违法行为识别
│   ├── 纠纷类型分类
│   └── 风险行为预警
└── 信息质量类 📋（待扩展）
    ├── 信息完整性评估
    ├── 逻辑一致性检查
    └── 异常数据识别
```

### 2.2 规则详细分析

#### A. 涉警当事人判断规则

**规则层次结构**：
```
Layer 1: 豁免情形检测（直接合格）
├── 撤警类关键词："请求撤警" "误报警" "不需要处理" "已协商解决"
├── 无效警情类："重复警情" "副单" "无效警情" "无效报警"
└── 无事实类："未发现报警情况" "现场无异常" "无警情发生"

Layer 2: 涉警当事人识别
├── 纳入范畴：纠纷双方、侵权行为人、受害人
└── 排除范畴：民警、辅警、路人、围观群众

Layer 3: 身份证号完整性验证
├── 18位标准格式验证
├── 同一姓名多次出现的信息合并
├── 护照等同身份证
└── 排除其他编号（手机号、车牌号等）
```

**技术实现策略**：**混合模式（Rule-then-LLM）**

#### B. 在校学生信息检查规则

**规则层次结构**：
```
Layer 1: 在校学生识别
├── 明确关键词：在校学生、学生、学号、年级
├── 学校类型：幼儿园/小学/初中/高中/中职/高校
└── 排除情况：成人教育、培训机构、已毕业

Layer 2: 学生身份信息完整性验证（6项）
├── ✅ 姓名
├── ✅ 身份证号
├── ✅ 学校全称
├── ✅ 在读年级
├── ✅ 院系/专业（小学/初中/高中无需）
└── ✅ 联系方式

Layer 3: 特殊情况处理
├── 辍学学生：仍需标明学籍
├── 休学学生：仍视为在校学生
└── 非全日制：不属于在校学生
```

**技术实现策略**：**LLM为主 + 规则验证**

---

## 3. 系统架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        内置全局标签系统                      │
├─────────────────────────────────────────────────────────────┤
│  管理层                                                      │
│  ├── 管理员创建/维护全局标签                                 │
│  ├── 普通用户只读使用                                        │
│  └── 版本管理（不覆盖历史）                                  │
├─────────────────────────────────────────────────────────────┤
│  配置层                                                      │
│  ├── 标签基本信息（名称、类型、描述）                        │
│  ├── LLM配置（模型选择、温度、提示词）                       │
│  ├── 提取器配置（规则预处理）                                │
│  └── 强化配置（二次分析）                                    │
├─────────────────────────────────────────────────────────────┤
│  执行层                                                      │
│  ├── 智能路由（自动选择大模型）                              │
│  ├── 规则预处理器（快速过滤）                                │
│  ├── LLM分析器（语义理解）                                  │
│  └── 二次强化器（质量保证）                                  │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 执行流程

#### 流程1：混合模式（Rule-then-LLM）

```
┌──────────────┐
│  输入数据    │
└──────┬───────┘
       │
       ↓
┌──────────────────────────────────────┐
│  步骤1：规则预处理器（同步）          │
│  ├── 提取18位身份证号                │
│  ├── 检测豁免关键词                  │
│  ├── 提取护照号码                    │
│  └── 执行时间：<50ms                 │
└──────┬───────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────┐
│  步骤2：智能路由（决策）              │
│  ├── 计算复杂度分数                  │
│  ├── 选择模型：DeepSeek 或 千问      │
│  └── 执行时间：<10ms                 │
└──────┬───────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────┐
│  步骤3：LLM分析（异步）              │
│  ├── 构建提示词                      │
│  ├── 调用大模型                      │
│  ├── 解析响应                        │
│  └── 执行时间：1-4秒                 │
└──────┬───────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────┐
│  步骤4：规则验证器（后验证）          │
│  ├── 验证18位身份证号格式            │
│  ├── 验证信息完整性                  │
│  └── 执行时间：<50ms                 │
└──────┬───────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────┐
│  步骤5：二次强化（可选）              │
│  ├── 置信度 < 75%                    │
│  ├── 触发二次分析                    │
│  └── 执行时间：+1-2秒                │
└──────┬───────────────────────────────┘
       │
       ↓
┌──────────────┐
│  输出结果    │
│  ├── 结论    │
│  ├── 理由    │
│  └── 置信度  │
└──────────────┘
```

### 3.3 智能路由算法

```java
/**
 * 智能路由：根据规则复杂度自动选择最优模型
 */
public String selectOptimalModel(Label label, Map<String, Object> rowData) {
    // 1. 检查标签是否指定了模型
    String specifiedModel = extractModelFromLabel(label);
    if (specifiedModel != null) {
        return specifiedModel;
    }

    // 2. 计算复杂度分数
    int complexityScore = calculateComplexity(label, rowData);

    // 3. 根据分数选择模型
    if (complexityScore >= 80) {
        return "deepseek-chat";  // DeepSeek 70B
    } else if (complexityScore >= 50) {
        return "qwen-plus";      // 千问32B
    } else {
        return "qwen-turbo";     // 千问7B（更快速）
    }
}

/**
 * 计算规则复杂度
 */
private int calculateComplexity(Label label, Map<String, Object> rowData) {
    int score = 0;

    // 1. 文本长度（复杂文本需要更强模型）
    int textLength = getCombinedTextLength(rowData);
    score += Math.min(textLength / 100, 30);  // 最高30分

    // 2. 规则层次（多层次规则需要更强模型）
    int ruleLayers = countRuleLayers(label);
    score += ruleLayers * 15;  // 每层15分

    // 3. 特殊情况数量（例外越多，需要越强模型）
    int exceptionCount = countExceptions(label);
    score += exceptionCount * 10;  // 每个例外10分

    // 4. 语义复杂度（需要推理的规则）
    if (needsInference(label)) {
        score += 20;  // 推理需求20分
    }

    // 5. 否定条件（否定条件更难理解）
    if (hasNegativeConditions(label)) {
        score += 15;  // 否定条件15分
    }

    return Math.min(score, 100);  // 最高100分
}
```

---

## 4. 大模型选型分析

### 4.1 模型对比矩阵

| 维度 | DeepSeek 70B | 千问32B | 千问7B | 推荐选择 |
|-----|-------------|---------|--------|---------|
| **参数规模** | 70B | 32B | 7B | - |
| **语义理解** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | DeepSeek |
| **推理能力** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | DeepSeek |
| **中文优化** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 千问系列 |
| **响应速度** | 2-4秒 | 1-2秒 | <1秒 | 千问7B |
| **API成本** | $0.08/次 | $0.03/次 | $0.01/次 | 千问7B |
| **稳定性** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 千问系列 |

### 4.2 规则推荐模型

| 规则名称 | 推荐模型 | 理由 |
|---------|---------|------|
| **涉警当事人判断** | DeepSeek 70B | 需要复杂的多层次推理 |
| **在校学生信息检查** | 千问32B | 主要是信息验证，推理需求较低 |
| **重大案件识别** | DeepSeek 70B | 需要深度语义理解 |
| **敏感信息检测** | 千问32B | 模式匹配为主 |
| **信息完整性评估** | 千问7B | 简单计数和验证 |

### 4.3 成本效益分析

**场景假设**：每月处理10万条警情

| 策略 | 月成本 | 年成本 | 准确率 | 性价比 |
|-----|--------|--------|--------|--------|
| **全DeepSeek** | $8,000 | $96,000 | 96% | 低 |
| **全千问32B** | $3,000 | $36,000 | 90% | 中 |
| **全千问7B** | $1,000 | $12,000 | 82% | 低 |
| **混合策略** | $4,500 | $54,000 | 95% | **最高** |

**混合策略成本计算**：
```
30% 复杂规则 → DeepSeek 70B ($0.08)
60% 中等规则 → 千问32B ($0.03)
10% 简单规则 → 千问7B ($0.01)

月成本 = 100,000 × (0.3×$0.08 + 0.6×$0.03 + 0.1×$0.01)
       = 100,000 × ($0.024 + $0.018 + $0.001)
       = $4,300
```

### 4.4 最终推荐

**分阶段部署**：

```
【阶段1：验证期（2周）】
├── 所有规则使用 DeepSeek 70B
├── 目标：建立准确率基准
└── 评估：收集性能数据

【阶段2：混合部署（4周）】
├── 实施智能路由
├── 复杂规则 → DeepSeek 70B
├── 中等规则 → 千问32B
├── 简单规则 → 千问7B
└── 目标：降低40%成本，保持95%准确率

【阶段3：优化期（持续）】
├── 根据实际数据调整路由阈值
├── 优化提示词模板
└── 探索其他模型
```

---

## 5. 技术实现方案

### 5.1 需要新增的提取器

#### A. PassportExtractor - 护照号提取器

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.ExtractedNumber;
import com.datalabeling.service.extraction.INumberExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 护照号提取器
 * 支持中国护照号和国际护照号提取
 */
@Component
public class PassportExtractor implements INumberExtractor {

    // 中国护照号（9位，G/P/S/D开头）
    private static final Pattern PASSPORT_CN = Pattern.compile(
        "\\b[GDPS]\\d{8}\\b"
    );

    // 护照号通用格式（字母+数字，6-12位）
    private static final Pattern PASSPORT_GENERIC = Pattern.compile(
        "\\b[A-Za-z]{1,2}\\d{6,9}\\b"
    );

    @Override
    public String getExtractorType() {
        return "passport";
    }

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();

        // 1. 提取中国护照号
        results.addAll(extractChinesePassport(text));

        // 2. 提取通用护照号（排除中国护照号）
        results.addAll(extractGenericPassport(text, results));

        return results;
    }

    /**
     * 提取中国护照号
     */
    private List<ExtractedNumber> extractChinesePassport(String text) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = PASSPORT_CN.matcher(text);

        while (matcher.find()) {
            String passport = matcher.group();
            results.add(ExtractedNumber.builder()
                .field("护照号")
                .value(passport)
                .confidence(0.90f)
                .validation("中国护照号（9位，" + passport.charAt(0) + "开头）")
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .build());
        }

        return results;
    }

    /**
     * 提取通用护照号
     */
    private List<ExtractedNumber> extractGenericPassport(String text,
                                                         List<ExtractedNumber> chinesePassports) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = PASSPORT_GENERIC.matcher(text);

        while (matcher.find()) {
            String passport = matcher.group();

            // 排除已提取的中国护照号
            boolean isChinesePassport = chinesePassports.stream()
                .anyMatch(e -> e.getValue().equals(passport));

            if (!isChinesePassport) {
                results.add(ExtractedNumber.builder()
                    .field("护照号")
                    .value(passport)
                    .confidence(0.75f)
                    .validation("通用护照号格式")
                    .startIndex(matcher.start())
                    .endIndex(matcher.end())
                    .build());
            }
        }

        return results;
    }
}
```

#### B. KeywordMatcherExtractor - 关键词匹配提取器

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.ExtractedNumber;
import com.datalabeling.service.extraction.INumberExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 关键词匹配提取器
 * 用于检测特定关键词的存在
 */
@Component
public class KeywordMatcherExtractor implements INumberExtractor {

    @Override
    public String getExtractorType() {
        return "keyword_match";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<String> keywords = (List<String>) options.get("keywords");
        String matchType = (String) options.getOrDefault("matchType", "any");

        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }

        List<ExtractedNumber> results = new ArrayList<>();
        List<String> matchedKeywords = new ArrayList<>();

        // 查找匹配的关键词
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                matchedKeywords.add(keyword);

                // 如果是any模式，找到一个就停止
                if ("any".equals(matchType)) {
                    break;
                }
            }
        }

        // 如果有匹配，返回结果
        if (!matchedKeywords.isEmpty()) {
            boolean requiresAll = "all".equals(matchType);
            boolean matched = requiresAll
                ? matchedKeywords.size() == keywords.size()
                : matchedKeywords.size() > 0;

            results.add(ExtractedNumber.builder()
                .field("匹配的关键词")
                .value(String.join(", ", matchedKeywords))
                .confidence(matched ? 0.95f : 0.0f)
                .validation("匹配到 " + matchedKeywords.size() + " 个关键词: "
                    + String.join(", ", matchedKeywords))
                .build());
        }

        return results;
    }
}
```

#### C. SchoolInfoExtractor - 学校信息提取器

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.ExtractedNumber;
import com.datalabeling.service.extraction.INumberExtractor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学校信息提取器
 * 用于识别学校类型和名称
 */
@Component
public class SchoolInfoExtractor implements INumberExtractor {

    // 学校类型关键词库
    private static final Map<String, String> SCHOOL_TYPES = new LinkedHashMap<>();
    static {
        SCHOOL_TYPES.put("幼儿园", "kindergarten");
        SCHOOL_TYPES.put("小学", "primary_school");
        SCHOOL_TYPES.put("初中", "junior_high");
        SCHOOL_TYPES.put("高中", "senior_high");
        SCHOOL_TYPES.put("中职", "vocational_school");
        SCHOOL_TYPES.put("技校", "technical_school");
        SCHOOL_TYPES.put("专科", "junior_college");
        SCHOOL_TYPES.put("本科", "undergraduate");
        SCHOOL_TYPES.put("硕士", "master");
        SCHOOL_TYPES.put("博士", "doctorate");
        SCHOOL_TYPES.put("大学", "university");
        SCHOOL_TYPES.put("学院", "college");
    }

    // 培训机构排除关键词
    private static final List<String> TRAINING_KEYWORDS = Arrays.asList(
        "培训", "补习", "辅导", "培训班", "培训学校", "培训机构"
    );

    // 学校名称正则
    private static final Pattern SCHOOL_NAME_PATTERN = Pattern.compile(
        "([\\u4e00-\\u9fa5]{2,15})(幼儿园|小学|中学|高中|中职|技校|学院|大学)"
    );

    @Override
    public String getExtractorType() {
        return "school_info";
    }

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();

        // 1. 检测是否是培训机构
        boolean isTraining = TRAINING_KEYWORDS.stream().anyMatch(text::contains);
        if (isTraining) {
            results.add(ExtractedNumber.builder()
                .field("机构类型")
                .value("培训机构")
                .confidence(0.90f)
                .validation("检测到培训机构关键词，不属于正规学校")
                .build());
            return results;
        }

        // 2. 识别学校类型
        String detectedSchoolType = null;
        for (Map.Entry<String, String> entry : SCHOOL_TYPES.entrySet()) {
            if (text.contains(entry.getKey())) {
                detectedSchoolType = entry.getValue();
                results.add(ExtractedNumber.builder()
                    .field("学校类型")
                    .value(detectedSchoolType)
                    .confidence(0.85f)
                    .validation("识别到学校类型: " + entry.getKey())
                    .build());
                break;
            }
        }

        // 3. 提取学校名称
        Matcher matcher = SCHOOL_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String schoolName = matcher.group(1) + matcher.group(2);
            results.add(ExtractedNumber.builder()
                .field("学校名称")
                .value(schoolName)
                .confidence(0.80f)
                .validation("提取的学校名称")
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .build());
        }

        return results;
    }

    /**
     * 判断学校类型是否需要院系/专业信息
     */
    public static boolean needsMajor(String schoolType) {
        if (schoolType == null) {
            return true;  // 默认需要
        }
        // 小学、初中、高中不需要院系/专业
        return !schoolType.equals("kindergarten")
            && !schoolType.equals("primary_school")
            && !schoolType.equals("junior_high")
            && !schoolType.equals("senior_high");
    }
}
```

### 5.2 修改现有提取器注册

```java
/**
 * 提取器注册表扩展
 */
@Component
public class ExtractorRegistry {

    @Autowired
    private IdCardExtractor idCardExtractor;

    @Autowired
    private BankCardExtractor bankCardExtractor;

    @Autowired
    private PhoneExtractor phoneExtractor;

    @Autowired
    private InvalidIdCardExtractor invalidIdCardExtractor;

    // 新增
    @Autowired
    private PassportExtractor passportExtractor;

    @Autowired
    private KeywordMatcherExtractor keywordMatcherExtractor;

    @Autowired
    private SchoolInfoExtractor schoolInfoExtractor;

    public INumberExtractor getExtractor(String type) {
        switch (type) {
            case "id_card":
                return idCardExtractor;
            case "bank_card":
                return bankCardExtractor;
            case "phone":
                return phoneExtractor;
            case "invalid_id_card":
                return invalidIdCardExtractor;
            // 新增
            case "passport":
                return passportExtractor;
            case "keyword_match":
                return keywordMatcherExtractor;
            case "school_info":
                return schoolInfoExtractor;
            default:
                throw new IllegalArgumentException("未知的提取器类型: " + type);
        }
    }
}
```

---

## 6. 数据库设计

### 6.1 提示词模板表（system_prompts）

```sql
-- 插入涉警当事人判断提示词模板
INSERT INTO system_prompts (code, name, type, category, content, is_default, is_active, created_at, updated_at)
VALUES (
    'police_personnel_check_v1',
    '涉警当事人信息完整性检查',
    'classification',
    'builtin',
    '你是警情质量检测专家，负责检查警情中"涉警当事人信息录入是否合格"。

=== 判断逻辑优先级 ===

【优先级1】直接合格情形检测
如果警情包含以下任一情形，直接判定为"合格"（是），无需检查身份证号：
1. 撤警类：请求撤警、误报警、不需要处理、已协商解决
2. 无效警情类：重复警情、副单、无效警情、无效报警
3. 无事实类：未发现报警情况、现场无异常、无警情发生

【优先级2】若不符合直接合格情形，进入检查流程

=== 涉警当事人定义 ===

【纳入范畴】
- 与警情事实有直接关系的：纠纷双方、侵权行为人、受害人
- 报警人若与警情有直接关联（事件当事人或直接受害者）

【排除范畴】
- 处警民警、交警、辅警
- 与警情无直接关系的路人、围观群众、陌生人
- 仅因路见不平而报警的非当事人
- 盗窃、损坏财物等警情中无法确定嫌疑人身份的
- 警情记录明确说明当事人不在现场、无法联系的

=== 合格标准 ===

1. 所有涉警当事人的身份证号码均完整（18位，格式正确）
2. 护照等同身份证，提供护照视为信息完整
3. 若同一姓名出现多次，某处有18位身份证号，视为信息完整
4. 18位数字若无其他标注（如手机号），视为身份证号

=== 不合格标准 ===

1. 存在任一涉警当事人身份证号码缺失、不完整或格式错误
2. 同一姓名多次出现，但所有记录均无18位身份证号
3. 18位数字被标注为其他用途（手机号、工号等）

{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}

=== 输出要求 ===
1. 如果合格，输出"是"，无需解释
2. 如果不合格，输出"否"，并说明不合格的人员姓名
3. 输出格式为JSON：
{
  "结论": "是/否",
  "理由": "判断依据"
}',
    0,
    1,
    NOW(),
    NOW()
);

-- 插入在校学生信息检查提示词模板
INSERT INTO system_prompts (code, name, type, category, content, is_default, is_active, created_at, updated_at)
VALUES (
    'student_info_check_v1',
    '在校学生信息完整性检查',
    'classification',
    'builtin',
    '你是警情信息处理专家，负责判断警情中是否涉及在校学生及其身份信息的完整性。

=== 在校学生定义 ===

【在校学生】是指在国家教育行政部门批准设立的教育机构注册的全日制在读学生：
- 幼儿园、小学、初中、普通高中
- 中等职业学校（含技工学校）
- 普通高等学校（含专科、本科、硕士、博士研究生）
- 辍学、休学的学生（仍需标明学籍）

【不属于在校学生】
- 已办理正式退学、毕业或肄业的
- 成人教育、函授、自考等非全日制学生
- 社会培训机构、非教育部门注册的学员

=== 学生身份信息（6项） ===

1. ✅ 姓名
2. ✅ 身份证号码
3. ✅ 学校全称（非简称）
4. ✅ 在读年级（如：大二、高三、中职一年级等）
5. ✅ 院系/专业（小学/初中/高中无需填写）
6. ✅ 联系方式（如手机号、电子邮箱等）

=== 判断逻辑 ===

【情况1】不包含在校学生 → 输出"是"
【情况2】包含在校学生，信息完整（6项） → 输出"是"
【情况3】包含在校学生，信息不完整 → 输出"否"

特别注意：
- 小学、初中、高中学生无需校验"院系/专业"
- 培训机构学生不属于在校学生
- 辍学学生仍需标明原学校学籍（如：2019级语文专业）
- 仅15岁且在学校门口，不明确说明为在校学生，不视为涉及学生

{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}

=== 输出要求 ===
输出JSON格式：
{
  "结论": "是/否",
  "理由": "判断依据"
}',
    0,
    1,
    NOW(),
    NOW()
);

-- 插入二次强化提示词模板
INSERT INTO system_prompts (code, name, type, category, content, is_default, is_active, created_at, updated_at)
VALUES (
    'police_personnel_enhancement_v1',
    '涉警当事人二次强化分析',
    'enhancement',
    'builtin',
    '你是数据质量审核专家，请对以下"涉警当事人信息完整性检查"的初步结果进行二次验证。

=== 任务信息 ===
标签名称：涉警当事人信息完整性检查

=== 原始数据 ===
{{row_data_json}}

=== 初步分析结果 ===
判断：{{initial_result}}
置信度：{{initial_confidence}}%
推理：{{initial_reasoning}}

{{#if validation_result}}
=== 规则验证结果 ===
{{validation_result}}
{{/if}}

=== 二次审核重点 ===
1. 豁免情形判断是否正确？（撤警、无效警情、无事实）
2. 涉警当事人识别是否准确？（是否误纳入民警、路人等）
3. 身份证号验证是否充分？（18位格式、护照等同）
4. 置信度评估是否合理？

=== 输出要求 ===
输出JSON格式：
{
  "final_result": "维持原判"或"修正为是/否",
  "final_confidence": 0-100,
  "validation_notes": "二次审核发现的问题或确认的理由",
  "should_adjust": true/false,
  "adjustment_reason": "如果需要修正，说明原因"
}',
    0,
    1,
    NOW(),
    NOW()
);

-- 插入学生信息二次强化提示词模板
INSERT INTO system_prompts (code, name, type, category, content, is_default, is_active, created_at, updated_at)
VALUES (
    'student_info_enhancement_v1',
    '在校学生信息二次强化分析',
    'enhancement',
    'builtin',
    '你是数据质量审核专家，请对以下"在校学生信息完整性检查"的初步结果进行二次验证。

=== 任务信息 ===
标签名称：在校学生信息完整性检查

=== 原始数据 ===
{{row_data_json}}

=== 初步分析结果 ===
判断：{{initial_result}}
置信度：{{initial_confidence}}%
推理：{{initial_reasoning}}

{{#if validation_result}}
=== 规则验证结果 ===
{{validation_result}}
{{/if}}

=== 二次审核重点 ===
1. 在校学生识别是否准确？（是否误识别培训机构）
2. 学校类型判断是否正确？（判断是否需要院系/专业）
3. 特殊情况处理是否正确？（辍学、休学等）
4. 信息完整性验证是否充分？（6项信息）

=== 输出要求 ===
输出JSON格式：
{
  "final_result": "维持原判"或"修正为是/否",
  "final_confidence": 0-100,
  "validation_notes": "二次审核发现的问题或确认的理由",
  "should_adjust": true/false,
  "adjustment_reason": "如果需要修正，说明原因"
}',
    0,
    1,
    NOW(),
    NOW()
);
```

### 6.2 内置全局标签表（labels）

```sql
-- 插入涉警当事人信息完整性检查标签
-- 假设管理员ID为1
INSERT INTO labels (
    user_id,
    name,
    version,
    scope,
    type,
    description,
    focus_columns,
    llm_config,
    preprocessing_mode,
    preprocessor_config,
    include_preprocessor_in_prompt,
    enable_enhancement,
    enhancement_config,
    is_active,
    created_at,
    updated_at
) VALUES (
    1,  -- 管理员ID
    '涉警当事人信息完整性检查',
    1,
    'global',
    'classification',
    '检查警情中涉警当事人的身份证号信息是否完整录入（18位标准格式或护照号）',
    '["警情内容", "当事人信息", "备注"]',
    '{
        "promptTemplateCode": "police_personnel_check_v1",
        "temperature": 0.1,
        "maxTokens": 500,
        "includeReasoning": true,
        "model": "deepseek-chat",
        "responseFormat": "json_object"
    }',
    'rule_then_llm',
    '{
        "extractors": [
            {
                "extractorType": "id_card",
                "field": "18位身份证号",
                "options": {
                    "include18Digit": true,
                    "include15Digit": false,
                    "includeLoose": false
                }
            },
            {
                "extractorType": "passport",
                "field": "护照号"
            },
            {
                "extractorType": "keyword_match",
                "field": "豁免关键词",
                "options": {
                    "keywords": [
                        "请求撤警", "误报警", "不需要处理", "已协商解决",
                        "重复警情", "副单", "无效警情", "无效报警",
                        "未发现报警情况", "现场无异常", "无警情发生"
                    ],
                    "matchType": "any"
                }
            }
        ]
    }',
    true,
    true,
    '{
        "triggerConfidence": 75,
        "promptTemplateCode": "police_personnel_enhancement_v1",
        "model": "deepseek-chat"
    }',
    true,
    NOW(),
    NOW()
);

-- 插入在校学生信息完整性检查标签
INSERT INTO labels (
    user_id,
    name,
    version,
    scope,
    type,
    description,
    focus_columns,
    llm_config,
    rule_supplement,
    enable_enhancement,
    enhancement_config,
    is_active,
    created_at,
    updated_at
) VALUES (
    1,  -- 管理员ID
    '在校学生信息完整性检查',
    1,
    'global',
    'classification',
    '检查警情中涉及的在校学生身份信息是否完整（姓名、身份证号、学校全称、年级、院系/专业、联系方式）',
    '["警情内容", "当事人信息"]',
    '{
        "promptTemplateCode": "student_info_check_v1",
        "temperature": 0.2,
        "maxTokens": 600,
        "includeReasoning": true,
        "model": "qwen-plus",
        "responseFormat": "json_object"
    }',
    '{
        "enabled": true,
        "mode": "post_validate",
        "rules": [
            {
                "ruleName": "student_info_6_items",
                "action": "require",
                "description": "在校学生必须包含6项信息"
            },
            {
                "ruleName": "school_type_check",
                "action": "boost",
                "description": "小学/初中/高中无需院系/专业信息",
                "boostScore": 0.1
            }
        ]
    }',
    true,
    '{
        "triggerConfidence": 70,
        "promptTemplateCode": "student_info_enhancement_v1",
        "model": "qwen-plus"
    }',
    true,
    NOW(),
    NOW()
);
```

---

## 7. 提示词模板设计

### 7.1 提示词模板变量系统

当前系统已支持的变量：

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `{{label_name}}` | 标签名称 | "涉警当事人信息完整性检查" |
| `{{label_description}}` | 标签描述 | "检查警情中涉警当事人的身份证号..." |
| `{{focus_columns}}` | 关注列 | "警情内容, 当事人信息" |
| `{{extract_fields}}` | 提取字段 | "18位身份证号, 护照号" |
| `{{row_data_json}}` | 原始数据JSON | `{"警情内容": "...", ...}` |
| `{{preprocessor_result}}` | 预处理结果 | "✓ 提取结果（2个）：..." |

### 7.2 条件块语法

```text
{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}
```

### 7.3 提示词优化建议

#### A. 结构化输出

使用 `response_format: "json_object"` 强制JSON输出：

```java
Map<String, String> responseFormat = new HashMap<>();
responseFormat.put("type", "json_object");
requestBody.put("response_format", responseFormat);
```

#### B. 温度参数

| 任务类型 | 推荐温度 | 理由 |
|---------|---------|------|
| **涉警当事人判断** | 0.1 | 需要严格按照规则判断 |
| **学生信息检查** | 0.2 | 允许一定的灵活性 |
| **二次强化分析** | 0.2 | 需要重新审视，但不能偏离太远 |

#### C. 少样本学习（Few-Shot Learning）

在提示词中包含示例：

```text
=== 示例 ===

【示例1：合格】
警情内容：报警人张三，身份证号110101199001011234，因误报警请求撤警。
输出：{"结论": "是", "理由": "请求撤警，属于直接合格情形"}

【示例2：不合格】
警情内容：报警人李四（无身份证号），与王五发生纠纷。
输出：{"结论": "否", "理由": "涉警当事人李四身份证号缺失"}
```

---

## 8. 实施路线图

### 8.1 阶段划分

```
阶段1：基础设施（1周）
├── 数据库变更
│   ├── 新增提示词模板
│   ├── 新增内置全局标签
│   └── 数据迁移脚本
├── 后端开发
│   ├── 新增3个提取器
│   ├── 智能路由服务
│   └── API接口扩展
└── 测试
    └── 单元测试

阶段2：核心功能（2周）
├── 标签1：涉警当事人信息完整性检查
│   ├── 提示词优化
│   ├── 测试数据集准备
│   ├── 准确率验证
│   └── 性能优化
└── 标签2：在校学生信息完整性检查
    ├── 提示词优化
    ├── 测试数据集准备
    ├── 准确率验证
    └── 性能优化

阶段3：集成与优化（1周）
├── 前端集成
│   ├── 标签选择器
│   ├── 结果展示
│   └── 进度跟踪
├── 智能路由调优
│   ├── 复杂度计算优化
│   ├── 模型选择阈值调整
│   └── 成本优化
└── 文档编写
    ├── 用户手册
    ├── API文档
    └── 运维手册

阶段4：部署与监控（1周）
├── 灰度发布
│   ├── 10%流量
│   ├── 50%流量
│   └── 100%流量
├── 监控告警
│   ├── 准确率监控
│   ├── 性能监控
│   └── 成本监控
└── 迭代优化
    └── 根据监控数据优化
```

### 8.2 里程碑

| 里程碑 | 完成时间 | 交付物 | 验收标准 |
|--------|---------|--------|---------|
| **M1: 基础设施完成** | 第1周 | 数据库、后端提取器、API | 单元测试通过率100% |
| **M2: 标签1实现** | 第2周 | 涉警当事人检查标签 | 准确率≥95% |
| **M3: 标签2实现** | 第3周 | 学生信息检查标签 | 准确率≥92% |
| **M4: 前端集成完成** | 第4周 | 前端界面、交互 | 用户验收通过 |
| **M5: 生产部署** | 第5周 | 灰度发布完成 | 系统稳定运行 |

### 8.3 风险与应对

| 风险 | 可能性 | 影响 | 应对措施 |
|-----|--------|------|---------|
| **大模型API不稳定** | 中 | 高 | 实现重试机制、降级到规则模式 |
| **准确率不达标** | 中 | 高 | 提示词优化、添加更多示例 |
| **成本超预算** | 中 | 中 | 智能路由、缓存常见结果 |
| **性能问题** | 低 | 中 | 异步处理、并发控制 |

---

## 9. 测试策略

### 9.1 测试数据集

#### A. 涉警当事人判断数据集

| 场景分类 | 数据量 | 预期结果 | 难度 |
|---------|--------|---------|------|
| **豁免情形** | 20条 | 是 | ⭐ |
| **信息完整** | 30条 | 是 | ⭐⭐ |
| **信息缺失** | 30条 | 否 | ⭐⭐ |
| **边界情况** | 20条 | 视情况 | ⭐⭐⭐⭐⭐ |
| **总计** | **100条** | - | - |

#### B. 在校学生信息检查数据集

| 场景分类 | 数据量 | 预期结果 | 难度 |
|---------|--------|---------|------|
| **不涉及学生** | 20条 | 是 | ⭐ |
| **信息完整** | 30条 | 是 | ⭐⭐ |
| **信息不完整** | 30条 | 否 | ⭐⭐⭐ |
| **特殊情况** | 20条 | 视情况 | ⭐⭐⭐⭐⭐ |
| **总计** | **100条** | - | - |

### 9.2 评估指标

#### A. 准确性指标

| 指标 | 公式 | 目标值 |
|-----|------|--------|
| **准确率 (Precision)** | TP / (TP + FP) | ≥95% |
| **召回率 (Recall)** | TP / (TP + FN) | ≥92% |
| **F1分数** | 2 × (Precision × Recall) / (Precision + Recall) | ≥93% |

#### B. 性能指标

| 指标 | 目标值 |
|-----|--------|
| **平均响应时间** | ≤3秒 |
| **95分位响应时间** | ≤5秒 |
| **并发处理能力** | ≥100 req/s |

#### C. 成本指标

| 指标 | 目标值 |
|-----|--------|
| **单次处理成本** | ≤$0.05 |
| **月度总成本** | ≤$5,000 |
| **成本降低率** | ≥40%（相比全DeepSeek） |

### 9.3 测试用例示例

#### 用例1：涉警当事人 - 豁免情形

```json
{
  "testCase": "涉警当事人_请求撤警",
  "input": {
    "警情内容": "报警人张三，因误报警请求撤警，已与对方协商解决",
    "当事人信息": "张三，无身份证号"
  },
  "expected": {
    "结论": "是",
    "理由": "包含'请求撤警'关键词，属于直接合格情形"
  }
}
```

#### 用例2：在校学生 - 培训机构

```json
{
  "testCase": "在校学生_培训机构非在校学生",
  "input": {
    "警情内容": "王雅慧（341721200612300529）在合肥金陵美食小吃培训学校学习烘焙时与学校方发生退费纠纷",
    "当事人信息": "王雅慧，341721200612300529"
  },
  "expected": {
    "结论": "是",
    "理由": "培训机构学生不属于在校学生"
  }
}
```

---

## 10. 成本估算

### 10.1 开发成本

| 角色 | 人数 | 周期 | 人周 | 人天 |
|-----|------|------|------|------|
| **后端开发** | 2人 | 4周 | 8 | 40 |
| **前端开发** | 1人 | 2周 | 2 | 10 |
| **测试工程师** | 1人 | 3周 | 3 | 15 |
| **DevOps** | 1人 | 1周 | 1 | 5 |
| **项目经理** | 1人 | 5周 | 5 | 25 |
| **总计** | - | - | **19人周** | **95人天** |

### 10.2 运营成本

#### A. 大模型API成本（月度）

假设每月处理10万条警情，使用混合策略：

| 规则类型 | 占比 | 模型 | 单次成本 | 月成本 |
|---------|------|------|---------|--------|
| **涉警当事人判断** | 30% | DeepSeek 70B | $0.08 | $2,400 |
| **在校学生信息检查** | 50% | 千问32B | $0.03 | $1,500 |
| **其他规则** | 20% | 千问7B | $0.01 | $200 |
| **总计** | 100% | - | - | **$4,100/月** |

#### B. 年度成本预估

| 成本项 | 月成本 | 年成本 |
|--------|--------|--------|
| **大模型API** | $4,100 | $49,200 |
| **服务器** | $500 | $6,000 |
| **存储** | $200 | $2,400 |
| **网络** | $100 | $1,200 |
| **维护** | $300 | $3,600 |
| **总计** | **$5,200** | **$62,400** |

### 10.3 ROI分析

#### 假设前提

- 当前人工处理成本：$2/条
- 自动化后成本：$0.05/条
- 每月处理量：10万条

#### 成本节约

| 项目 | 当前 | 自动化后 | 节约 |
|-----|------|---------|------|
| **单条处理成本** | $2.00 | $0.05 | $1.95 |
| **月度成本** | $200,000 | $5,200 | $194,800 |
| **年度成本** | $2,400,000 | $62,400 | $2,337,600 |

#### 投资回报

- **开发成本**：95人天 × $500/天 = $47,500
- **年度节约**：$2,337,600
- **ROI**：($2,337,600 - $47,500) / $47,500 × 100% = **4,818%**
- **回本周期**：$47,500 / ($194,800 / 12) ≈ **3个月**

---

## 11. 附录

### 11.1 术语表

| 术语 | 说明 |
|-----|------|
| **涉警当事人** | 与警情事实有直接关系的纠纷双方、侵权行为人、受害人等 |
| **在校学生** | 在国家教育行政部门批准设立的教育机构注册的全日制在读学生 |
| **18位身份证号** | 中国第二代身份证号码，18位数字+校验位 |
| **豁免情形** | 无需检查身份证号信息的特殊情况 |
| **智能路由** | 根据规则复杂度自动选择最优大模型的机制 |
| **混合模式** | 规则预处理器 + LLM分析 + 二次强化的组合模式 |

### 11.2 参考资料

- [DeepSeek API文档](https://platform.deepseek.com/api-docs/)
- [千问API文档](https://help.aliyun.com/zh/dashscope/)
- [项目架构文档](./标签功能架构优化设计V3.1.md)
- [号码提取方案](./号码提取与通用信息提取综合方案.md)

### 11.3 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|-----|------|---------|------|
| V1.0 | 2026-01-26 | 初始版本 | AI Assistant |

---

**文档结束**

如有疑问，请联系项目团队。
