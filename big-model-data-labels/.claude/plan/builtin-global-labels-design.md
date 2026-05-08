# 内置全局标签系统 - 功能需求设计与实现方案

> **项目**: 智能数据标注与分析平台
> **版本**: V1.0
> **创建日期**: 2026-01-26
> **目标**: 基于检测规则文档构建完善的内置全局标签体系

---

## 📋 目录

1. [项目背景与目标](#1-项目背景与目标)
2. [业务需求分析](#2-业务需求分析)
3. [内置全局标签体系设计](#3-内置全局标签体系设计)
4. [技术架构设计](#4-技术架构设计)
5. [详细实现方案](#5-详细实现方案)
6. [测试策略](#6-测试策略)
7. [实施路线图](#7-实施路线图)
8. [运维与监控](#8-运维与监控)
9. [成本效益分析](#9-成本效益分析)

---

## 1. 项目背景与目标

### 1.1 当前系统现状

智能数据标注平台已具备以下能力：

| 能力模块 | 当前状态 | 说明 |
|---------|---------|------|
| **标签管理** | ✅ 已实现 | 支持用户自定义标签,版本管理,三种作用域 |
| **内置提取器** | ✅ 已实现 | 身份证/手机/银行卡/邮箱/日期/金额等10+提取器 |
| **号码意图系统** | ✅ 已实现 | NumberIntentEvaluator,支持exists/extract/invalid等任务 |
| **预处理模式** | ✅ 已实现 | llm_only/rule_only/rule_then_llm三种模式 |
| **二次强化** | ✅ 已实现 | 低置信度结果二次分析 |
| **全局标签** | ✅ 已实现 | 管理员维护,所有用户可见 |

### 1.2 业务需求概述

基于**检测规则.docx**文档分析,核心业务需求如下:

#### 需求1: 涉警当事人信息完整性检查

**业务目标**: 检查警情中涉警当事人的身份证号信息是否完整录入

**复杂度**: ⭐⭐⭐⭐⭐ (最高)

**判断逻辑层次**:
```
Layer 1: 豁免情形检测（直接合格）
├── 撤警类关键词："请求撤警"、"误报警"、"不需要处理"、"已协商解决"
├── 无效警情类："重复警情"、"副单"、"无效警情"
└── 无事实类："未发现报警情况"、"现场无异常"、"无警情发生"

Layer 2: 涉警当事人识别
├── 纳入：纠纷双方、侵权行为人、受害人
└── 排除：民警、辅警、路人、围观群众

Layer 3: 身份证号完整性验证
├── 18位标准格式验证
├── 同一姓名信息合并
├── 护照等同身份证
└── 排除其他编号（手机号、车牌号等）
```

**输出格式**:
```json
{
  "结论": "是/否",
  "理由": "判断依据"
}
```

#### 需求2: 在校学生信息完整性检查

**业务目标**: 检查警情中涉及的在校学生身份信息是否完整

**复杂度**: ⭐⭐⭐⭐

**判断逻辑**:
```
Layer 1: 在校学生识别
├── 明确关键词："在校学生"、"学生"、"学号"、"年级"
├── 学校类型：幼儿园/小学/初中/高中/中职/高校
└── 排除：成人教育、培训机构、已毕业

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

**输出格式**:
```json
{
  "结论": "是/否",
  "理由": "判断依据"
}
```

### 1.3 项目目标

| 维度 | 当前状态 | 目标状态 | 提升幅度 |
|-----|---------|---------|---------|
| **内置标签数量** | 0个(需新建) | 2+个,可扩展至10+ | - |
| **规则覆盖场景** | 号码提取 | 人员信息、案件特征等 | +200% |
| **检测准确率** | N/A | 95%+ | - |
| **处理效率** | N/A | 智能路由,混合模式 | +40% |
| **可维护性** | 人工维护 | 系统化管理 | 显著提升 |

---

## 2. 业务需求分析

### 2.1 核心业务规则详细分析

#### 规则A: 涉警当事人信息完整性检查

**1. 业务背景**
警情数据质量是警务工作的基础,涉警当事人信息完整性直接影响后续工作。

**2. 合格标准**
1. 所有涉警当事人的身份证号码均完整(18位,格式正确)
2. 护照等同身份证,提供护照视为信息完整
3. 若同一姓名出现多次,某处有18位身份证号,视为信息完整
4. 18位数字若无其他标注(如手机号),视为身份证号

**3. 不合格标准**
1. 存在任一涉警当事人身份证号码缺失、不完整或格式错误
2. 同一姓名多次出现,但所有记录均无18位身份证号
3. 18位数字被标注为其他用途(手机号、工号等)

**4. 豁免情形**
- 撤警类:请求撤警、误报警、不需要处理、已协商解决
- 无效警情类:重复警情、副单、无效警情、无效报警
- 无事实类:未发现报警情况、现场无异常、无警情发生

**5. 涉警当事人定义**

**纳入范畴**:
- 与警情事实有直接关系的:纠纷双方、侵权行为人、受害人
- 报警人若与警情有直接关联(事件当事人或直接受害者)

**排除范畴**:
- 处警民警、交警、辅警
- 与警情无直接关系的路人、围观群众、陌生人
- 仅因路见不平而报警的非当事人
- 盗窃、损坏财物等警情中无法确定嫌疑人身份的
- 警情记录明确说明当事人不在现场、无法联系的

#### 规则B: 在校学生信息完整性检查

**1. 业务背景**
在校学生是特殊群体,需要特别关注和记录其身份信息。

**2. 在校学生定义**
在国家教育行政部门批准设立的教育机构注册的全日制在读学生:
- 幼儿园、小学、初中、普通高中
- 中等职业学校(含技工学校)
- 普通高等学校(含专科、本科、硕士、博士研究生)
- 辍学、休学的学生(仍需标明学籍)

**3. 不属于在校学生**
- 已办理正式退学、毕业或肄业的
- 成人教育、函授、自考等非全日制学生
- 社会培训机构、非教育部门注册的学员

**4. 学生身份信息(6项)**
1. ✅ 姓名
2. ✅ 身份证号码
3. ✅ 学校全称(非简称)
4. ✅ 在读年级(如:大二、高三、中职一年级等)
5. ✅ 院系/专业(小学/初中/高中无需填写)
6. ✅ 联系方式(如手机号、电子邮箱等)

**5. 特殊情况处理**
- 小学、初中、高中学生无需校验"院系/专业"
- 培训机构学生不属于在校学生
- 辍学学生仍需标明原学校学籍(如:2019级语文专业)
- 仅15岁且在学校门口,不明确说明为在校学生,不视为涉及学生

### 2.2 数据来源分析

**数据来源**: Excel/CSV文件上传,或外部数据库导入

**数据格式**:
```
| 警情编号 | 警情内容 | 当事人信息 | 报警时间 | 备注 |
|---------|---------|-----------|---------|------|
| JC001   | 张三与李四纠纷... | 张三,110101199001011234 | 2026-01-26 | 协商解决 |
```

**关键字段**:
- `警情内容`: 警情详细描述
- `当事人信息`: 当事人姓名、身份证号、联系方式等
- `备注`: 其他补充信息

### 2.3 业务流程分析

```
【业务流程】
数据上传 → 解析入库 → 选择标签 → 执行分析 → 查看结果 → 人工审核 → 导出/同步

【关键节点】
1. 数据上传: 支持 .xlsx/.xls/.csv 格式
2. 标签选择: 从内置全局标签库选择
3. 执行分析: 异步任务处理,实时进度更新
4. 结果展示: 显示结论、理由、置信度
5. 人工修正: 支持手动修改标签结果
6. 结果导出: 导出为Excel/CSV或同步到数据库
```

---

## 3. 内置全局标签体系设计

### 3.1 标签分类体系

```
内置全局标签
├── 人员信息完整性类
│   ├── 涉警当事人信息完整性检查 ✅ (核心需求)
│   ├── 在校学生信息完整性检查 ✅ (核心需求)
│   └── [预留] 精神障碍患者信息检查 📋
├── 案件特征识别类 📋 (二期扩展)
│   ├── 重大案件识别
│   ├── 敏感信息检测
│   └── 紧急程度评估
├── 信息质量评估类 📋 (二期扩展)
│   ├── 信息完整性评估
│   ├── 逻辑一致性检查
│   └── 异常数据识别
└── 行为模式识别类 📋 (三期扩展)
    ├── 违法行为识别
    ├── 纠纷类型分类
    └── 风险行为预警
```

### 3.2 标签元数据定义

每个内置全局标签包含以下元数据:

| 字段 | 说明 | 示例值 |
|-----|------|--------|
| **name** | 标签名称 | "涉警当事人信息完整性检查" |
| **code** | 标签代码(唯一) | "police_personnel_integrity_check" |
| **category** | 标签分类 | "person_info_integrity" |
| **version** | 版本号 | 1 |
| **scope** | 作用域 | "global" |
| **type** | 标签类型 | "classification" |
| **description** | 标签描述 | "检查警情中涉警当事人的身份证号信息..." |
| **focus_columns** | 重点关注列 | ["警情内容", "当事人信息", "备注"] |
| **preprocessing_mode** | 预处理模式 | "rule_then_llm" |
| **preprocessor_config** | 预处理器配置 | {...} |
| **llm_config** | LLM配置 | {...} |
| **enable_enhancement** | 启用二次强化 | true |
| **enhancement_config** | 强化配置 | {...} |
| **is_active** | 是否激活 | true |
| **created_by** | 创建者(管理员ID) | 1 |
| **builtin_level** | 内置级别 | "system" (系统内置) |

### 3.3 标签配置模板

#### 标签1: 涉警当事人信息完整性检查

```json
{
  "name": "涉警当事人信息完整性检查",
  "code": "police_personnel_integrity_check",
  "category": "person_info_integrity",
  "version": 1,
  "scope": "global",
  "type": "classification",
  "description": "检查警情中涉警当事人的身份证号信息是否完整录入(18位标准格式或护照号)",
  "focus_columns": ["警情内容", "当事人信息", "备注"],
  "preprocessing_mode": "rule_then_llm",
  "preprocessor_config": {
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
  },
  "llm_config": {
    "promptTemplateCode": "police_personnel_check_v1",
    "temperature": 0.1,
    "maxTokens": 500,
    "includeReasoning": true,
    "model": "deepseek-chat",
    "responseFormat": "json_object"
  },
  "include_preprocessor_in_prompt": true,
  "enable_enhancement": true,
  "enhancement_config": {
    "triggerConfidence": 75,
    "promptTemplateCode": "police_personnel_enhancement_v1",
    "model": "deepseek-chat"
  },
  "is_active": true,
  "builtin_level": "system"
}
```

#### 标签2: 在校学生信息完整性检查

```json
{
  "name": "在校学生信息完整性检查",
  "code": "student_info_integrity_check",
  "category": "person_info_integrity",
  "version": 1,
  "scope": "global",
  "type": "classification",
  "description": "检查警情中涉及的在校学生身份信息是否完整(姓名、身份证号、学校全称、年级、院系/专业、联系方式)",
  "focus_columns": ["警情内容", "当事人信息"],
  "preprocessing_mode": "rule_then_llm",
  "preprocessor_config": {
    "extractors": [
      {
        "extractorType": "school_info",
        "field": "学校信息"
      },
      {
        "extractorType": "keyword_match",
        "field": "学生关键词",
        "options": {
          "keywords": ["在校学生", "学生", "学号", "年级", "幼儿园", "小学", "中学", "高中", "中职", "技校", "学院", "大学"],
          "matchType": "any"
        }
      }
    ]
  },
  "llm_config": {
    "promptTemplateCode": "student_info_check_v1",
    "temperature": 0.2,
    "maxTokens": 600,
    "includeReasoning": true,
    "model": "qwen-plus",
    "responseFormat": "json_object"
  },
  "include_preprocessor_in_prompt": true,
  "enable_enhancement": true,
  "enhancement_config": {
    "triggerConfidence": 70,
    "promptTemplateCode": "student_info_enhancement_v1",
    "model": "qwen-plus"
  },
  "rule_supplement": {
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
  },
  "is_active": true,
  "builtin_level": "system"
}
```

---

## 4. 技术架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     内置全局标签系统架构                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    表现层 (Presentation Layer)             │ │
│  │  ├── 标签管理界面 (Label Management UI)                    │ │
│  │  ├── 标签选择器 (Label Selector)                           │ │
│  │  ├── 结果展示 (Result Display)                             │ │
│  │  └── 进度跟踪 (Progress Tracking)                          │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              ↕ REST API                          │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   业务逻辑层 (Business Layer)              │ │
│  │  ├── LabelService (标签管理服务)                           │ │
│  │  ├── BuiltinLabelService (内置标签服务) ⭐ 新增            │ │
│  │  ├── AnalysisTaskAsyncService (异步任务服务)              │ │
│  │  └── DeepSeekService (大模型服务)                         │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              ↕                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   处理引擎层 (Processing Engine Layer)     │ │
│  │  ├── LabelProcessor (标签处理器)                           │ │
│  │  ├── PreprocessorChain (预处理链) ⭐ 增强                  │ │
│  │  │   ├── NegativeConditionPreprocessor (负向条件预处理)    │ │
│  │  │   └── NumberEvidenceExtractor (号码证据提取器)         │ │
│  │  ├── LLMAnalyzer (大模型分析器)                            │ │
│  │  └── EnhancementAnalyzer (二次强化分析器)                  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              ↕                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   提取器层 (Extractor Layer)               │ │
│  │  ├── ExtractorRegistry (提取器注册中心)                    │ │
│  │  ├── IdCardExtractor (身份证提取器)                        │ │
│  │  ├── PhoneExtractor (手机号提取器)                         │ │
│  │  ├── BankCardExtractor (银行卡提取器)                      │ │
│  │  ├── PassportExtractor (护照提取器) ⭐ 新增                │ │
│  │  ├── KeywordMatcherExtractor (关键词匹配) ⭐ 新增         │ │
│  │  └── SchoolInfoExtractor (学校信息提取器) ⭐ 新增         │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              ↕                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   数据访问层 (Data Access Layer)           │ │
│  │  ├── LabelRepository (标签仓库)                            │ │
│  │  ├── SystemPromptRepository (提示词仓库)                  │ │
│  │  ├── DataRowRepository (数据行仓库)                        │ │
│  │  └── AnalysisTaskRepository (任务仓库)                     │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                              ↕                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                   数据存储层 (Data Storage Layer)           │ │
│  │  ├── MySQL 8.0+ (关系型数据库)                             │ │
│  │  ├── Redis 7+ (缓存)                                       │ │
│  │  └── File System (文件存储)                                │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 核心流程设计

#### 流程1: 标签执行流程

```
┌──────────────────┐
│  输入: 数据行    │
└────────┬─────────┘
         │
         ↓
┌──────────────────────────────────────────┐
│  步骤1: 预处理 (Preprocessing)           │
│  ├── 提取号码证据(身份证/护照/关键词)    │
│  ├── 负向条件预处理                      │
│  └── 执行时间: <50ms                    │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────────────┐
│  步骤2: 智能路由 (Smart Routing)         │
│  ├── 计算复杂度分数                      │
│  ├── 选择最优模型 (DeepSeek/千问)       │
│  └── 执行时间: <10ms                    │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────────────┐
│  步骤3: LLM分析 (LLM Analysis)           │
│  ├── 构建提示词(含预处理结果)            │
│  ├── 调用大模型                          │
│  ├── 解析响应                            │
│  └── 执行时间: 1-4秒                    │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────────────┐
│  步骤4: 后验证 (Post Validation)         │
│  ├── 规则验证(格式/完整性)               │
│  └── 执行时间: <50ms                    │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────────────────────────────┐
│  步骤5: 二次强化 (Enhancement) [可选]    │
│  ├── 置信度 < 阈值(75%)                 │
│  ├── 触发二次分析                        │
│  └── 执行时间: +1-2秒                   │
└────────┬─────────────────────────────────┘
         │
         ↓
┌──────────────────┐
│  输出: 标签结果  │
│  ├── 结论(是/否) │
│  ├── 理由        │
│  └── 置信度      │
└──────────────────┘
```

#### 流程2: 内置标签管理流程

```
┌─────────────────────────────────────┐
│  管理员操作                          │
├─────────────────────────────────────┤
│  1. 查看内置全局标签列表             │
│  2. 查看标签详情                     │
│  3. 创建新版本(不覆盖历史)           │
│  4. 启用/禁用标签                    │
│  5. 查看使用统计                     │
│  6. [不可删除]系统内置标签            │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  普通用户操作                        │
├─────────────────────────────────────┤
│  1. 查看全局标签列表(只读)           │
│  2. 查看标签详情                     │
│  3. 选择标签进行分析                 │
│  4. 查看标签使用说明                 │
│  5. [不可修改]系统内置标签            │
└─────────────────────────────────────┘
```

---

## 5. 详细实现方案

### 5.1 数据库设计

#### 5.1.1 现有表结构扩展

**labels 表扩展** (已支持,无需修改):

现有字段已足够支持内置全局标签:
- `scope`: "global" (全局标签)
- `type`: "classification" (分类判断)
- `preprocessing_mode`: "rule_then_llm"
- `preprocessor_config`: JSON配置
- `llm_config`: JSON配置
- `enable_enhancement`: boolean
- `enhancement_config`: JSON配置

**新增字段** (可选,用于标识系统内置):
```sql
ALTER TABLE labels
ADD COLUMN builtin_level VARCHAR(20) DEFAULT 'custom' COMMENT '内置级别: system(系统内置) / custom(用户自定义)',
ADD COLUMN builtin_category VARCHAR(50) COMMENT '内置分类: person_info_integrity / case_feature / data_quality等',
ADD INDEX idx_builtin_level (builtin_level),
ADD INDEX idx_builtin_category (builtin_category);
```

#### 5.1.2 提示词模板数据

**system_prompts 表插入数据**:

```sql
-- 涉警当事人检查主提示词
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

-- 涉警当事人二次强化提示词
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

-- 在校学生信息检查主提示词
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

-- 在校学生二次强化提示词
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

#### 5.1.3 内置全局标签初始化数据

**labels 表插入数据** (假设管理员ID为1):

```sql
-- 涉警当事人信息完整性检查标签
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
    builtin_level,
    builtin_category,
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
    'system',
    'person_info_integrity',
    NOW(),
    NOW()
);

-- 在校学生信息完整性检查标签
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
    builtin_level,
    builtin_category,
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
    'rule_then_llm',
    '{
        "extractors": [
            {
                "extractorType": "school_info",
                "field": "学校信息"
            },
            {
                "extractorType": "keyword_match",
                "field": "学生关键词",
                "options": {
                    "keywords": ["在校学生", "学生", "学号", "年级", "幼儿园", "小学", "中学", "高中", "中职", "技校", "学院", "大学"],
                    "matchType": "any"
                }
            }
        ]
    }',
    true,
    true,
    '{
        "triggerConfidence": 70,
        "promptTemplateCode": "student_info_enhancement_v1",
        "model": "qwen-plus"
    }',
    true,
    'system',
    'person_info_integrity',
    NOW(),
    NOW()
);
```

### 5.2 后端实现

#### 5.2.1 新增提取器实现

**A. PassportExtractor - 护照号提取器**

文件: `backend/src/main/java/com/datalabeling/service/extraction/impl/PassportExtractor.java`

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 护照号提取器
 *
 * <p>提取功能：
 * - 中国护照号（G/P/S/D开头，9位）
 * - 国际护照号（通用格式）
 *
 * <p>业务场景：
 * - 涉警当事人信息检查（护照等同身份证）
 * - 涉外人员身份识别
 */
@Slf4j
@Component
public class PassportExtractor extends AbstractEnhancedExtractor {

    // 中国护照号（G/P/S/D开头，9位）
    private static final Pattern PASSPORT_CN = Pattern.compile("\\b[GDPS]\\d{8}\\b");

    // 护照号通用格式（字母+数字，6-12位）
    private static final Pattern PASSPORT_GENERIC = Pattern.compile("\\b[A-Za-z]{1,2}\\d{6,9}\\b");

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("passport")
        .name("护照号提取器")
        .description("提取中国护照号和国际护照号")
        .category("builtin")
        .outputField("护照号")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("证件", "身份信息", "护照"))
        .useCase("涉警当事人信息检查、涉外人员身份识别")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_cn_only")
                .name("仅包含中国护照")
                .description("是否仅提取中国护照号")
                .type("boolean")
                .defaultValue(false)
                .build()
        ))
        .build();

    @Override
    public ExtractorMetadata getMetadata() {
        return METADATA;
    }

    @Override
    protected List<ExtractorPattern> getPatterns() {
        return Arrays.asList(
            ExtractorPattern.highPriority("cn_passport", "\\b[GDPS]\\d{8}\\b", 0.95f),
            ExtractorPattern.of("generic_passport", "\\b[A-Za-z]{1,2}\\d{6,9}\\b", 0.75f)
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String passport = matcher.group();

        // 判断是否为中国护照号
        boolean isCnPassport = passport.matches("[GDPS]\\d{8}");

        // 检查是否仅包含中国护照
        boolean includeCnOnly = Boolean.TRUE.equals(options.get("include_cn_only"));
        if (includeCnOnly && !isCnPassport) {
            return null;
        }

        String validation = isCnPassport
            ? "中国护照号（9位，" + passport.charAt(0) + "开头）"
            : "通用护照号格式";

        float confidence = isCnPassport ? 0.90f : 0.75f;

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(passport)
            .rawValue(passport)
            .confidence(confidence)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning(isCnPassport ? "中国护照号码" : "护照号码")
            .dataType("passport")
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_cn_only", false);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "张三，中国护照号G12345678",
                "[\"G12345678\"]",
                "提取中国护照号"
            ),
            ExtractorExample.of(
                "John Doe, passport AB123456",
                "[\"AB123456\"]",
                "提取国际护照号"
            )
        );
    }

    @Override
    public String getExtractorType() {
        return "passport";
    }
}
```

**B. KeywordMatcherExtractor - 关键词匹配提取器**

文件: `backend/src/main/java/com/datalabeling/service/extraction/impl/KeywordMatcherExtractor.java`

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词匹配提取器
 *
 * <p>提取功能：
 * - 检测文本中是否包含指定关键词
 * - 支持多种匹配模式（任意匹配/全部匹配）
 *
 * <p>业务场景：
 * - 豁免情形检测（撤警、无效警情等）
 * - 快速规则过滤
 */
@Slf4j
@Component
public class KeywordMatcherExtractor extends AbstractEnhancedExtractor {

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("keyword_match")
        .name("关键词匹配提取器")
        .description("检测文本中是否包含指定关键词")
        .category("builtin")
        .outputField("匹配的关键词")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("关键词", "匹配", "规则"))
        .useCase("豁免情形检测、快速规则过滤")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("keywords")
                .name("关键词列表")
                .description("要匹配的关键词列表")
                .type("string")
                .defaultValue("")
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("match_type")
                .name("匹配类型")
                .description("any: 任意匹配, all: 全部匹配")
                .type("select")
                .selectOptions(Arrays.asList("any", "all"))
                .defaultValue("any")
                .build()
        ))
        .build();

    @Override
    public ExtractorMetadata getMetadata() {
        return METADATA;
    }

    @Override
    protected List<ExtractorPattern> getPatterns() {
        // 关键词匹配不使用正则模式
        return Collections.emptyList();
    }

    @Override
    public List<EnhancedExtractedResult> extract(String text, Map<String, Object> options) {
        List<String> keywords = getKeywords(options);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        String matchType = (String) options.getOrDefault("match_type", "any");
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

        // 判断是否满足匹配条件
        boolean matched = "all".equals(matchType)
            ? matchedKeywords.size() == keywords.size()
            : matchedKeywords.size() > 0;

        if (!matched) {
            return Collections.emptyList();
        }

        // 构建结果
        EnhancedExtractedResult result = EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(String.join(", ", matchedKeywords))
            .rawValue(String.join(", ", matchedKeywords))
            .confidence(matched ? 0.95f : 0.0f)
            .validation("匹配到 " + matchedKeywords.size() + " 个关键词: " + String.join(", ", matchedKeywords))
            .validationStatus("valid")
            .businessMeaning("检测到关键词")
            .dataType("keywords")
            .attributes(Map.of("matchedKeywords", matchedKeywords, "matchCount", matchedKeywords.size()))
            .build();

        return Collections.singletonList(result);
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        // 不使用，直接重写extract方法
        return null;
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("keywords", Collections.emptyList());
        options.put("match_type", "any");
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "报警人请求撤警，已协商解决",
                "[\"请求撤警\"]",
                "匹配豁免关键词"
            )
        );
    }

    @Override
    public String getExtractorType() {
        return "keyword_match";
    }

    /**
     * 从配置中获取关键词列表
     */
    @SuppressWarnings("unchecked")
    private List<String> getKeywords(Map<String, Object> options) {
        Object keywordsObj = options.get("keywords");
        if (keywordsObj instanceof List) {
            return (List<String>) keywordsObj;
        }
        if (keywordsObj instanceof String) {
            String keywordsStr = (String) keywordsObj;
            if (!keywordsStr.isEmpty()) {
                return Arrays.asList(keywordsStr.split(","));
            }
        }
        return Collections.emptyList();
    }
}
```

**C. SchoolInfoExtractor - 学校信息提取器**

文件: `backend/src/main/java/com/datalabeling/service/extraction/impl/SchoolInfoExtractor.java`

```java
package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学校信息提取器
 *
 * <p>提取功能：
 * - 识别学校类型（幼儿园/小学/中学/大学等）
 * - 判断是否为培训机构
 * - 提取学校名称
 *
 * <p>业务场景：
 * - 在校学生信息检查
 * - 学校类型判断
 */
@Slf4j
@Component
public class SchoolInfoExtractor extends AbstractEnhancedExtractor {

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

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("school_info")
        .name("学校信息提取器")
        .description("识别学校类型和名称，判断是否为培训机构")
        .category("builtin")
        .outputField("学校信息")
        .dataType("json")
        .multiValue(false)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("教育", "学校", "学生"))
        .useCase("在校学生信息检查、学校类型判断")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("exclude_training")
                .name("排除培训机构")
                .description("是否排除培训机构")
                .type("boolean")
                .defaultValue(true)
                .build()
        ))
        .build();

    @Override
    public ExtractorMetadata getMetadata() {
        return METADATA;
    }

    @Override
    protected List<ExtractorPattern> getPatterns() {
        return Arrays.asList(
            ExtractorPattern.of("school_name", "([\\u4e00-\\u9fa5]{2,15})(幼儿园|小学|中学|高中|中职|技校|学院|大学)", 0.85f)
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        Map<String, Object> attributes = new HashMap<>();
        List<String> validationSteps = new ArrayList<>();

        // 1. 检测是否是培训机构
        boolean isTraining = TRAINING_KEYWORDS.stream().anyMatch(text::contains);
        boolean excludeTraining = Boolean.TRUE.equals(options.getOrDefault("exclude_training", true));

        if (isTraining && excludeTraining) {
            attributes.put("机构类型", "培训机构");
            validationSteps.add("✗ 检测到培训机构关键词，不属于正规学校");

            return EnhancedExtractedResult.builder()
                .field(getMetadata().getOutputField())
                .value("培训机构")
                .rawValue("培训机构")
                .confidence(0.90f)
                .validation("检测到培训机构关键词，不属于正规学校")
                .validationStatus("invalid")
                .businessMeaning("培训机构学生不属于在校学生")
                .dataType("school_info")
                .attributes(attributes)
                .build();
        }

        validationSteps.add("✓ 非培训机构");

        // 2. 识别学校类型
        String detectedSchoolType = null;
        for (Map.Entry<String, String> entry : SCHOOL_TYPES.entrySet()) {
            if (text.contains(entry.getKey())) {
                detectedSchoolType = entry.getValue();
                attributes.put("学校类型", entry.getValue());
                validationSteps.add("✓ 识别到学校类型: " + entry.getKey());
                break;
            }
        }

        // 3. 提取学校名称
        List<String> schoolNames = new ArrayList<>();
        Matcher nameMatcher = SCHOOL_NAME_PATTERN.matcher(text);
        while (nameMatcher.find()) {
            String schoolName = nameMatcher.group(1) + nameMatcher.group(2);
            schoolNames.add(schoolName);
        }

        if (!schoolNames.isEmpty()) {
            attributes.put("学校名称", schoolNames);
            validationSteps.add("✓ 提取到 " + schoolNames.size() + " 个学校名称");
        }

        // 构建结果
        String resultValue = detectedSchoolType != null ? detectedSchoolType : "未识别";

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(resultValue)
            .rawValue(resultValue)
            .confidence(detectedSchoolType != null ? 0.85f : 0.50f)
            .validation(String.join("; ", validationSteps))
            .validationStatus(detectedSchoolType != null ? "valid" : "uncertain")
            .businessMeaning("学校信息，用于判断是否涉及在校学生")
            .dataType("school_info")
            .attributes(attributes)
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("exclude_training", true);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "清华大学计算机系",
                "{\"学校类型\": \"university\", \"学校名称\": [\"清华大学\"]}",
                "提取大学信息"
            ),
            ExtractorExample.of(
                "北京四中高中部",
                "{\"学校类型\": \"senior_high\", \"学校名称\": [\"北京四中\"]}",
                "提取高中信息"
            ),
            ExtractorExample.of(
                "新东方英语培训学校",
                "{\"机构类型\": \"培训机构\"}",
                "识别培训机构"
            )
        );
    }

    @Override
    public String getExtractorType() {
        return "school_info";
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

#### 5.2.2 内置标签服务实现

**BuiltinLabelService - 内置标签服务**

文件: `backend/src/main/java/com/datalabeling/service/BuiltinLabelService.java`

```java
package com.datalabeling.service;

import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 内置全局标签服务
 *
 * <p>核心功能：
 * - 查询内置全局标签列表
 * - 按分类筛选内置标签
 * - 创建新版本（管理员）
 * - 启用/禁用标签（管理员）
 * - 查看使用统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltinLabelService {

    private final LabelRepository labelRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    /**
     * 获取所有内置全局标签（最新版本）
     *
     * @param builtinCategory 内置分类过滤（可选）
     * @param pageable 分页参数
     * @param httpRequest HTTP请求
     * @return 内置全局标签列表
     */
    public PageResult<LabelVO> getBuiltinLabels(String builtinCategory, Pageable pageable, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 记录审计日志
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("builtinCategory", builtinCategory);
        auditDetails.put("page", pageable != null ? pageable.getPageNumber() + 1 : null);
        auditDetails.put("size", pageable != null ? pageable.getPageSize() : null);
        auditService.recordAdminRead(currentUserId, "admin_read_builtin_labels", "label", null, auditDetails, httpRequest);

        // 查询管理员用户ID列表
        List<Integer> adminUserIds = userRepository.findAdminUserIds();
        if (adminUserIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0L, 1, pageable.getPageSize());
        }

        // 查询内置全局标签
        Page<Label> page;
        if (builtinCategory != null && !builtinCategory.isEmpty()) {
            page = labelRepository.findBuiltinGlobalByCategoryAndLatest(adminUserIds, builtinCategory, pageable);
        } else {
            page = labelRepository.findBuiltinGlobalActiveLatest(adminUserIds, pageable);
        }

        List<LabelVO> voList = page.getContent().stream()
            .map(this::toBuiltinLabelVO)
            .toList();

        return PageResult.of(voList, page.getTotalElements(),
            page.getNumber() + 1, page.getSize());
    }

    /**
     * 按分类获取内置全局标签
     *
     * @param builtinCategory 内置分类
     * @return 标签列表
     */
    public List<LabelVO> getBuiltinLabelsByCategory(String builtinCategory, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 记录审计日志
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("builtinCategory", builtinCategory);
        auditService.recordAdminRead(currentUserId, "admin_read_builtin_labels_by_category", "label", null, auditDetails, httpRequest);

        // 查询管理员用户ID列表
        List<Integer> adminUserIds = userRepository.findAdminUserIds();
        if (adminUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 查询内置全局标签
        List<Label> labels = labelRepository.findBuiltinGlobalByCategoryAndLatest(
            adminUserIds,
            builtinCategory,
            null  // pageable = null 表示不分页
        );

        return labels.stream()
            .map(this::toBuiltinLabelVO)
            .toList();
    }

    /**
     * 获取内置标签分类列表
     *
     * @return 分类列表
     */
    public List<Map<String, Object>> getBuiltinCategories(HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        auditService.recordAdminRead(currentUserId, "admin_read_builtin_categories", "label", null, null, httpRequest);

        // 返回预定义的分类
        List<Map<String, Object>> categories = new ArrayList<>();

        categories.add(Map.of(
            "code", "person_info_integrity",
            "name", "人员信息完整性",
            "description", "检查人员身份信息的完整性",
            "icon", "👤",
            "order", 1
        ));

        categories.add(Map.of(
            "code", "case_feature",
            "name", "案件特征识别",
            "description", "识别案件的特征和属性",
            "icon", "🔍",
            "order", 2
        ));

        categories.add(Map.of(
            "code", "data_quality",
            "name", "信息质量评估",
            "description", "评估数据的质量和完整性",
            "icon", "✓",
            "order", 3
        ));

        categories.add(Map.of(
            "code", "behavior_pattern",
            "name", "行为模式识别",
            "description", "识别行为模式和风险",
            "icon", "⚠️",
            "order", 4
        ));

        return categories;
    }

    /**
     * 创建内置标签新版本（仅管理员）
     *
     * @param labelId 原标签ID
     * @param request 更新请求
     * @return 新版本标签
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO createBuiltinLabelNewVersion(Integer labelId, UpdateLabelRequest request) {
        // 权限检查：仅管理员可维护系统内置标签
        if (!securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置全局标签仅管理员可维护");
        }

        // 查询原标签
        Label originalLabel = labelRepository.findById(labelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        // 检查是否为系统内置标签
        if (!"system".equals(originalLabel.getBuiltinLevel())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该标签不是系统内置标签");
        }

        // 查询同名标签的最大版本号
        Integer maxVersion = labelRepository.findMaxVersionByUserIdAndName(
            originalLabel.getUserId(),
            originalLabel.getName()
        );

        // 创建新版本
        Label newVersion = Label.builder()
            .userId(originalLabel.getUserId())
            .name(originalLabel.getName())
            .version(maxVersion + 1)
            .scope(originalLabel.getScope())
            .type(originalLabel.getType())
            .description(request.getDescription())
            .focusColumns(request.getFocusColumns())
            .extractFields(request.getExtractFields())
            .extractorConfig(request.getExtractorConfig())
            .preprocessingMode(request.getPreprocessingMode())
            .preprocessorConfig(request.getPreprocessorConfig())
            .includePreprocessorInPrompt(request.getIncludePreprocessorInPrompt())
            .enableEnhancement(request.getEnableEnhancement())
            .enhancementConfig(request.getEnhancementConfig())
            .isActive(true)
            .builtinLevel(originalLabel.getBuiltinLevel())
            .builtinCategory(originalLabel.getBuiltinCategory())
            .build();

        newVersion = labelRepository.save(newVersion);

        log.info("内置标签新版本创建成功: id={}, name={}, version={}",
            newVersion.getId(), newVersion.getName(), newVersion.getVersion());

        return toBuiltinLabelVO(newVersion);
    }

    /**
     * 启用/禁用内置标签（仅管理员）
     *
     * @param labelId 标签ID
     * @param active 是否激活
     */
    @Transactional(rollbackFor = Exception.class)
    public void setBuiltinLabelActive(Integer labelId, boolean active) {
        // 权限检查：仅管理员可维护系统内置标签
        if (!securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置全局标签仅管理员可维护");
        }

        // 查询标签
        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        // 检查是否为系统内置标签
        if (!"system".equals(label.getBuiltinLevel())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该标签不是系统内置标签");
        }

        // 更新激活状态
        label.setIsActive(active);
        labelRepository.save(label);

        log.info("内置标签状态更新成功: id={}, name={}, active={}",
            labelId, label.getName(), active);
    }

    /**
     * 转换为内置标签VO
     */
    private LabelVO toBuiltinLabelVO(Label label) {
        LabelVO vo = new LabelVO();
        vo.setId(label.getId());
        vo.setName(label.getName());
        vo.setVersion(label.getVersion());
        vo.setScope(label.getScope());
        vo.setType(label.getType());
        vo.setDescription(label.getDescription());
        vo.setFocusColumns(label.getFocusColumns());
        vo.setExtractFields(label.getExtractFields());
        vo.setPreprocessingMode(label.getPreprocessingMode());
        vo.setIncludePreprocessorInPrompt(label.getIncludePreprocessorInPrompt());
        vo.setEnableEnhancement(label.getEnableEnhancement());
        vo.setIsActive(label.getIsActive());
        vo.setCreatedAt(label.getCreatedAt());
        vo.setUpdatedAt(label.getUpdatedAt());

        // 内置标签特有字段
        vo.setBuiltinLevel(label.getBuiltinLevel());
        vo.setBuiltinCategory(label.getBuiltinCategory());

        // 查询使用统计
        // TODO: 实现使用统计查询

        return vo;
    }
}
```

#### 5.2.3 数据库Repository扩展

**LabelRepository 扩展**

文件: `backend/src/main/java/com/datalabeling/repository/LabelRepository.java`

```java
/**
 * 查询"系统内置全局标签"（管理员创建的 global，最新版本）
 *
 * @param adminUserIds 管理员用户ID列表
 * @return 内置全局标签列表
 */
@Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' AND l.builtinLevel = 'system' AND l.isActive = true AND l.version = (SELECT MAX(l2.version) FROM Label l2 WHERE l2.userId = l.userId AND l2.name = l.name)")
List<Label> findBuiltinGlobalActiveLatest(@Param("adminUserIds") List<Integer> adminUserIds);

/**
 * 分页查询"系统内置全局标签"
 *
 * @param adminUserIds 管理员用户ID列表
 * @param pageable 分页参数
 * @return 内置全局标签分页结果
 */
@Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' AND l.builtinLevel = 'system' AND l.isActive = true AND l.version = (SELECT MAX(l2.version) FROM Label l2 WHERE l2.userId = l.userId AND l2.name = l.name)")
Page<Label> findBuiltinGlobalActiveLatest(@Param("adminUserIds") List<Integer> adminUserIds, Pageable pageable);

/**
 * 按分类查询"系统内置全局标签"
 *
 * @param adminUserIds 管理员用户ID列表
 * @param builtinCategory 内置分类
 * @param pageable 分页参数（可选）
 * @return 内置全局标签列表
 */
@Query("SELECT l FROM Label l WHERE l.userId IN :adminUserIds AND l.scope = 'global' AND l.builtinLevel = 'system' AND l.builtinCategory = :builtinCategory AND l.isActive = true AND l.version = (SELECT MAX(l2.version) FROM Label l2 WHERE l2.userId = l.userId AND l2.name = l.name)")
List<Label> findBuiltinGlobalByCategoryAndLatest(@Param("adminUserIds") List<Integer> adminUserIds, @Param("builtinCategory") String builtinCategory, Pageable pageable);
```

#### 5.2.4 Controller接口扩展

**BuiltinLabelController - 内置标签控制器**

文件: `backend/src/main/java/com/datalabeling/controller/BuiltinLabelController.java`

```java
package com.datalabeling.controller;

import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.UpdateLabelRequest;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.service.BuiltinLabelService;
import com.datalabeling.util.SecurityUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 内置全局标签控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/builtin-labels")
@Api(tags = "内置全局标签管理")
@RequiredArgsConstructor
public class BuiltinLabelController {

    private final BuiltinLabelService builtinLabelService;
    private final SecurityUtil securityUtil;

    /**
     * 获取内置全局标签列表（分页）
     */
    @GetMapping
    @ApiOperation("获取内置全局标签列表")
    public Result<PageResult<LabelVO>> getBuiltinLabels(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10) Pageable pageable,
            HttpServletRequest request) {
        log.info("获取内置全局标签列表: category={}, pageable={}", category, pageable);
        PageResult<LabelVO> result = builtinLabelService.getBuiltinLabels(category, pageable, request);
        return Result.success(result);
    }

    /**
     * 获取内置标签分类列表
     */
    @GetMapping("/categories")
    @ApiOperation("获取内置标签分类")
    public Result<List<Map<String, Object>>> getBuiltinCategories(HttpServletRequest request) {
        log.info("获取内置标签分类列表");
        List<Map<String, Object>> categories = builtinLabelService.getBuiltinCategories(request);
        return Result.success(categories);
    }

    /**
     * 按分类获取内置全局标签
     */
    @GetMapping("/by-category/{category}")
    @ApiOperation("按分类获取内置全局标签")
    public Result<List<LabelVO>> getBuiltinLabelsByCategory(
            @PathVariable String category,
            HttpServletRequest request) {
        log.info("按分类获取内置全局标签: category={}", category);
        List<LabelVO> labels = builtinLabelService.getBuiltinLabelsByCategory(category, request);
        return Result.success(labels);
    }

    /**
     * 创建内置标签新版本（仅管理员）
     */
    @PostMapping("/{id}/new-version")
    @ApiOperation("创建内置标签新版本")
    public Result<LabelVO> createBuiltinLabelNewVersion(
            @PathVariable Integer id,
            @RequestBody UpdateLabelRequest request) {
        log.info("创建内置标签新版本: id={}", id);

        // 权限检查
        if (!securityUtil.isAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅管理员可创建内置标签新版本");
        }

        LabelVO newVersion = builtinLabelService.createBuiltinLabelNewVersion(id, request);
        return Result.success(newVersion);
    }

    /**
     * 启用/禁用内置标签（仅管理员）
     */
    @PutMapping("/{id}/active")
    @ApiOperation("启用/禁用内置标签")
    public Result<Void> setBuiltinLabelActive(
            @PathVariable Integer id,
            @RequestParam boolean active) {
        log.info("{}内置标签: id={}", active ? "启用" : "禁用", id);

        // 权限检查
        if (!securityUtil.isAdmin()) {
            return Result.error(ErrorCode.FORBIDDEN, "仅管理员可修改内置标签状态");
        }

        builtinLabelService.setBuiltinLabelActive(id, active);
        return Result.success();
    }
}
```

### 5.3 前端实现

#### 5.3.1 内置标签列表页面

文件: `frontend/src/views/BuiltinLabelsView.vue`

```vue
<template>
  <div class="builtin-labels-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>内置全局标签库</h2>
      <p class="subtitle">系统预置的高质量标签,可直接用于数据标注</p>
    </div>

    <!-- 分类筛选 -->
    <div class="category-filter">
      <el-radio-group v-model="selectedCategory" @change="handleCategoryChange">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button
          v-for="cat in categories"
          :key="cat.code"
          :label="cat.code"
        >
          {{ cat.icon }} {{ cat.name }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 标签列表 -->
    <el-table
      :data="labels"
      v-loading="loading"
      stripe
      class="labels-table"
    >
      <el-table-column prop="name" label="标签名称" min-width="200">
        <template #default="{ row }">
          <div class="label-name">
            <el-icon v-if="row.builtinLevel === 'system'" class="builtin-icon">
              <Stamp />
            </el-icon>
            {{ row.name }}
          </div>
        </template>
      </el-table-column>

      <el-table-column label="分类" width="150">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.builtinCategory)" size="small">
            {{ getCategoryName(row.builtinCategory) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip />

      <el-table-column label="模式" width="150">
        <template #default="{ row }">
          <div class="mode-badge">
            <span v-if="row.preprocessingMode === 'llm_only'">💬 LLM</span>
            <span v-else-if="row.preprocessingMode === 'rule_only'">⚡ Rule</span>
            <span v-else-if="row.preprocessingMode === 'rule_then_llm'">🔀 Hybrid</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="版本" width="80" prop="version" />

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            v-model="row.isActive"
            @change="handleToggleActive(row)"
            :disabled="!isAdmin"
          />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleViewDetail(row)">
            <el-icon><View /></el-icon> 详情
          </el-button>
          <el-button
            v-if="isAdmin"
            size="small"
            @click="handleCreateNewVersion(row)"
          >
            <el-icon><DocumentAdd /></el-icon> 新版本
          </el-button>
          <el-button size="small" @click="handleUseLabel(row)">
            <el-icon><Select /></el-icon> 使用
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, sizes, total"
        @current-change="fetchLabels"
        @size-change="fetchLabels"
      />
    </div>

    <!-- 标签详情对话框 -->
    <BuiltinLabelDetailDialog
      v-model="detailDialogVisible"
      :label="selectedLabel"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Stamp, View, DocumentAdd, Select } from '@element-plus/icons-vue'
import BuiltinLabelDetailDialog from '@/components/labels/BuiltinLabelDetailDialog.vue'
import * as builtinLabelsApi from '@/api/builtin-labels'

const loading = ref(false)
const labels = ref([])
const categories = ref([])
const selectedCategory = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const isAdmin = ref(false)  // 从用户信息中获取

const detailDialogVisible = ref(false)
const selectedLabel = ref(null)

onMounted(() => {
  isAdmin.value = checkIsAdmin()  // 实现管理员检查
  fetchCategories()
  fetchLabels()
})

async function fetchCategories() {
  try {
    const resp = await builtinLabelsApi.getCategories()
    categories.value = resp.data
  } catch (e) {
    ElMessage.error('加载分类失败')
  }
}

async function fetchLabels() {
  loading.value = true
  try {
    const resp = await builtinLabelsApi.listLabels({
      category: selectedCategory.value,
      page: page.value,
      size: size.value
    })
    labels.value = resp.data.items
    total.value = resp.data.total
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function handleCategoryChange() {
  page.value = 1
  fetchLabels()
}

async function handleToggleActive(row) {
  try {
    await builtinLabelsApi.setActive(row.id, row.isActive)
    ElMessage.success(row.isActive ? '已启用' : '已禁用')
  } catch (e) {
    ElMessage.error('操作失败')
    row.isActive = !row.isActive  // 回滚状态
  }
}

function handleViewDetail(row) {
  selectedLabel.value = row
  detailDialogVisible.value = true
}

function handleCreateNewVersion(row) {
  ElMessageBox.confirm(
    '创建新版本将保留当前版本作为历史记录,是否继续?',
    '创建新版本',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    // TODO: 实现创建新版本功能
    ElMessage.info('功能开发中')
  }).catch(() => {
    // 用户取消
  })
}

function handleUseLabel(row) {
  ElMessage.success(`已选择标签: ${row.name}`)
  // TODO: 将选中的标签传递给数据分析任务
}

function getCategoryName(category: string) {
  const cat = categories.value.find(c => c.code === category)
  return cat ? cat.name : category
}

function getCategoryTagType(category: string) {
  const typeMap = {
    'person_info_integrity': 'primary',
    'case_feature': 'success',
    'data_quality': 'warning',
    'behavior_pattern': 'danger'
  }
  return typeMap[category] || 'info'
}

function checkIsAdmin() {
  // TODO: 实现管理员检查逻辑
  return false
}
</script>

<style scoped>
.builtin-labels-view {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
}

.subtitle {
  margin: 0;
  color: #64748b;
  font-size: 14px;
}

.category-filter {
  margin-bottom: 24px;
  display: flex;
  gap: 12px;
}

.labels-table {
  margin-bottom: 16px;
}

.label-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.builtin-icon {
  color: #f59e0b;
  font-size: 18px;
}

.mode-badge {
  font-size: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
}
</style>
```

---

## 6. 测试策略

### 6.1 测试数据集准备

#### 6.1.1 涉警当事人判断测试数据集

| 场景分类 | 数据量 | 预期结果 | 难度 |
|---------|--------|---------|------|
| **豁免情形** | 20条 | 是 | ⭐ |
| **信息完整** | 30条 | 是 | ⭐⭐ |
| **信息缺失** | 30条 | 否 | ⭐⭐ |
| **边界情况** | 20条 | 视情况 | ⭐⭐⭐⭐⭐ |
| **总计** | **100条** | - | - |

**测试用例示例**:

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

#### 6.1.2 在校学生信息检查测试数据集

| 场景分类 | 数据量 | 预期结果 | 难度 |
|---------|--------|---------|------|
| **不涉及学生** | 20条 | 是 | ⭐ |
| **信息完整** | 30条 | 是 | ⭐⭐ |
| **信息不完整** | 30条 | 否 | ⭐⭐⭐ |
| **特殊情况** | 20条 | 视情况 | ⭐⭐⭐⭐⭐ |
| **总计** | **100条** | - | - |

**测试用例示例**:

```json
{
  "testCase": "在校学生_培训机构",
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

### 6.2 评估指标

#### 6.2.1 准确性指标

| 指标 | 公式 | 目标值 |
|-----|------|--------|
| **准确率 (Precision)** | TP / (TP + FP) | ≥95% |
| **召回率 (Recall)** | TP / (TP + FN) | ≥92% |
| **F1分数** | 2 × (Precision × Recall) / (Precision + Recall) | ≥93% |

#### 6.2.2 性能指标

| 指标 | 目标值 |
|-----|--------|
| **平均响应时间** | ≤3秒 |
| **95分位响应时间** | ≤5秒 |
| **并发处理能力** | ≥100 req/s |

### 6.3 测试方法

1. **单元测试**: 对每个新增的提取器进行单元测试
2. **集成测试**: 测试标签执行的完整流程
3. **准确率测试**: 使用测试数据集验证标签准确率
4. **性能测试**: 测试并发处理能力
5. **压力测试**: 测试系统极限性能

---

## 7. 实施路线图

### 7.1 阶段划分

```
【阶段1: 基础设施】(1周)
├── 数据库变更
│   ├── 新增内置标签相关字段
│   ├── 插入提示词模板数据
│   └── 插入内置全局标签数据
├── 后端开发
│   ├── 新增3个提取器
│   ├── BuiltinLabelService实现
│   └── API接口扩展
└── 前端开发
    ├── 内置标签列表页面
    └── 标签详情对话框

【阶段2: 核心功能】(2周)
├── 标签1: 涉警当事人信息完整性检查
│   ├── 提示词优化
│   ├── 测试数据集准备
│   ├── 准确率验证
│   └── 性能优化
└── 标签2: 在校学生信息完整性检查
    ├── 提示词优化
    ├── 测试数据集准备
    ├── 准确率验证
    └── 性能优化

【阶段3: 集成与优化】(1周)
├── 前端集成
│   ├── 标签选择器集成
│   ├── 结果展示优化
│   └── 进度跟踪优化
├── 系统优化
│   ├── 性能优化
│   ├── 成本优化
│   └── 用户体验优化
└── 文档编写
    ├── 用户手册
    ├── API文档
    └── 运维手册

【阶段4: 部署与监控】(1周)
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

### 7.2 里程碑

| 里程碑 | 完成时间 | 交付物 | 验收标准 |
|--------|---------|--------|---------|
| **M1: 基础设施完成** | 第1周 | 数据库、后端、前端基础 | 单元测试通过率100% |
| **M2: 标签1实现** | 第2周 | 涉警当事人检查标签 | 准确率≥95% |
| **M3: 标签2实现** | 第3周 | 学生信息检查标签 | 准确率≥92% |
| **M4: 集成完成** | 第4周 | 前端集成、优化 | 用户验收通过 |
| **M5: 生产部署** | 第5周 | 灰度发布完成 | 系统稳定运行 |

---

## 8. 运维与监控

### 8.1 监控指标

#### 8.1.1 准确率监控

- 实时准确率统计
- 人工修正率监控
- 置信度分布分析
- 错误案例分析

#### 8.1.2 性能监控

- API响应时间
- 任务处理时间
- 并发处理能力
- 系统资源使用率

#### 8.1.3 成本监控

- 大模型API调用量
- API成本统计
- 成本趋势分析
- 成本优化建议

### 8.2 告警规则

| 告警项 | 阈值 | 级别 | 处理措施 |
|-------|------|------|---------|
| **准确率下降** | <90% | 严重 | 立即通知开发团队,暂停使用 |
| **响应时间过长** | >5秒 | 警告 | 检查系统负载,考虑扩容 |
| **API成本异常** | >预算150% | 警告 | 分析成本异常原因,优化提示词 |
| **系统错误率** | >5% | 严重 | 立即排查错误原因 |

---

## 9. 成本效益分析

### 9.1 开发成本

| 角色 | 人数 | 周期 | 人周 | 人天 |
|-----|------|------|------|------|
| **后端开发** | 2人 | 4周 | 8 | 40 |
| **前端开发** | 1人 | 2周 | 2 | 10 |
| **测试工程师** | 1人 | 3周 | 3 | 15 |
| **DevOps** | 1人 | 1周 | 1 | 5 |
| **项目经理** | 1人 | 5周 | 5 | 25 |
| **总计** | - | - | **19人周** | **95人天** |

### 9.2 运营成本

#### 9.2.1 大模型API成本（月度）

假设每月处理10万条警情,使用混合策略:

| 规则类型 | 占比 | 模型 | 单次成本 | 月成本 |
|---------|------|------|---------|--------|
| **涉警当事人判断** | 30% | DeepSeek 70B | $0.08 | $2,400 |
| **在校学生信息检查** | 50% | 千问32B | $0.03 | $1,500 |
| **其他规则** | 20% | 千问7B | $0.01 | $200 |
| **总计** | 100% | - | - | **$4,100/月** |

#### 9.2.2 年度成本预估

| 成本项 | 月成本 | 年成本 |
|--------|--------|--------|
| **大模型API** | $4,100 | $49,200 |
| **服务器** | $500 | $6,000 |
| **存储** | $200 | $2,400 |
| **网络** | $100 | $1,200 |
| **维护** | $300 | $3,600 |
| **总计** | **$5,200** | **$62,400** |

### 9.3 ROI分析

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

- **开发成本**: 95人天 × $500/天 = $47,500
- **年度节约**: $2,337,600
- **ROI**: ($2,337,600 - $47,500) / $47,500 × 100% = **4,818%**
- **回本周期**: $47,500 / ($194,800 / 12) ≈ **3个月**

---

## 10. 总结

### 10.1 核心价值

本方案实现了一个完善的内置全局标签体系,具有以下核心价值:

1. **业务价值**
   - 自动化检测警情数据质量
   - 提高数据录入完整性
   - 降低人工审核成本

2. **技术价值**
   - 可扩展的标签架构
   - 智能模型选择
   - 混合处理模式

3. **经济价值**
   - 显著降低人工成本
   - 提高处理效率
   - 快速投资回报

### 10.2 关键成功因素

1. **提示词质量**: 经过充分优化和测试
2. **模型选择**: 根据规则复杂度智能选择
3. **数据质量**: 高质量测试数据集
4. **持续优化**: 基于监控数据持续改进

### 10.3 扩展路线

本方案为未来扩展奠定了基础:

- **二期**: 案件特征识别类标签
- **三期**: 信息质量评估类标签
- **四期**: 行为模式识别类标签

---

**文档版本**: V1.0
**创建日期**: 2026-01-26
**作者**: AI Assistant
**状态**: 待评审

---

*本文档基于检测规则文档和现有系统架构,详细设计了内置全局标签体系的完整实现方案。如有疑问,请联系项目团队。*
