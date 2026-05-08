# 增强型预置提取器系统

## 概述

本系统提供了一个**Java代码驱动的预置提取器框架**，支持通过编写Java类来创建逻辑严密的提取器，精准提取各种业务数据，并自动生成大模型友好的上下文信息。

### 核心优势

1. **Java代码驱动** - 通过编写Java类实现提取器，逻辑严密、易于测试
2. **自动注册发现** - 基于Spring的自动装配，无需手动注册
3. **元数据支持** - 提取器自带完整的元数据（名称、描述、选项、示例）
4. **上下文增强** - 提取结果包含业务含义、验证信息、附加属性
5. **大模型友好** - 自动生成结构化提示词，便于大模型理解
6. **高度可扩展** - 基于AbstractEnhancedExtractor基类，轻松创建新提取器

## 系统架构

```
┌───────────────────────────────────────────────────────────────┐
│                        前端 Vue3                              │
│  ┌─────────────────┐    ┌─────────────────┐                  │
│  │ ExtractorsView  │    │  LabelsView     │                  │
│  │  提取器管理     │    │  标签配置       │                  │
│  └────────┬────────┘    └────────┬────────┘                  │
└───────────┼──────────────────────┼───────────────────────────┘
            │ HTTP API             │
            ▼                      ▼
┌───────────────────────────────────────────────────────────────┐
│                   EnhancedExtractorController                  │
│              /api/enhanced-extractors/*                        │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────┐
│              EnhancedExtractionOrchestrator                    │
│                   (增强型提取器协调器)                          │
│  - 统一提取接口                                                │
│  - 选项合并和默认值处理                                        │
│  - 结果过滤和分组                                              │
│  - 大模型提示词生成                                            │
└───────────────────────────┬───────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────┐
│                   ExtractorRegistry                            │
│                      (提取器注册中心)                           │
│  - 自动注册所有IEnhancedExtractor实现                          │
│  - 代码/分类/标签索引                                          │
│  - 别名匹配（id_card、idcard、idCard）                         │
└─────────────────────┬─────────────────────────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────────────────────────┐
│              预置提取器实现 (IEnhancedExtractor)               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │IdCard    │ │  Email   │ │   Date   │ │  Money   │  ...   │
│  │Extractor │ │Extractor │ │Extractor │ │Extractor │         │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘         │
└───────────────────────────────────────────────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────────────────────────┐
│              AbstractEnhancedExtractor                         │
│                   (提取器基类)                                 │
│  - 通用的提取流程                                              │
│  - 正则匹配和结果处理                                          │
│  - 上下文提取                                                  │
│  - 去重和过滤                                                  │
└───────────────────────────────────────────────────────────────┘
```

## 已实现的预置提取器

| 提取器 | 代码 | 功能 | 准确度 | 性能 |
|--------|------|------|--------|------|
| 身份证号提取器 | `id_card` | 提取18/15位身份证，校验位验证，地区码识别 | high | fast |
| 银行卡号提取器 | `bank_card` | 提取银行卡号，Luhn算法验证 | high | fast |
| 手机号提取器 | `phone` | 提取手机号，识别运营商 | high | fast |
| 邮箱地址提取器 | `email` | 提取邮箱，识别服务商（QQ、163、Gmail等） | high | fast |
| 日期时间提取器 | `date` | 提取ISO/中文/时间戳格式，自动标准化 | high | fast |
| 金额提取器 | `money` | 提取中文/阿拉伯数字金额，货币识别 | high | medium |
| IP地址提取器 | `ip_address` | 提取IPv4/IPv6，识别私有/公网地址 | high | fast |
| URL提取器 | `url` | 提取HTTP/HTTPS/FTP URL，解析组件 | high | fast |
| 车牌号提取器 | `car_plate` | 提取传统/新能源/军警车牌，识别省份 | high | fast |
| 公司名称提取器 | `company_name` | 提取公司名称，识别企业类型 | medium | medium |

## 核心接口和类

### 1. IEnhancedExtractor (增强型提取器接口)

所有预置提取器必须实现此接口：

```java
public interface IEnhancedExtractor extends INumberExtractor {
    // 获取元数据
    ExtractorMetadata getMetadata();

    // 增强型提取（返回带上下文的结果）
    List<EnhancedExtractedResult> extractWithContext(String text, Map<String, Object> options);

    // 构建大模型提示词
    String buildLLMPromptContext(List<EnhancedExtractedResult> results);

    // 获取默认选项
    Map<String, Object> getDefaultOptions();

    // 获取示例数据
    List<ExtractorExample> getExamples();
}
```

### 2. AbstractEnhancedExtractor (提取器基类)

简化提取器开发的基类，只需实现：

```java
public abstract class AbstractEnhancedExtractor implements IEnhancedExtractor {
    // 定义正则表达式列表
    protected abstract List<ExtractorPattern> getPatterns();

    // 处理单个匹配结果
    protected abstract EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options);

    // 实现其他接口方法
    public ExtractorMetadata getMetadata();  // 必须实现
    public List<ExtractorExample> getExamples();  // 必须实现
}
```

### 3. 增强型提取结果

```java
public class EnhancedExtractedResult extends ExtractedNumber {
    private String rawValue;           // 原始值
    private Object normalizedValue;    // 标准化值
    private String dataType;           // 数据类型
    private String businessMeaning;    // 业务含义
    private String validationStatus;   // 验证状态
    private Map<String, Object> attributes;  // 附加属性
    private String context;            // 上下文文本
}
```

## 如何创建新提取器

### 步骤1：创建提取器类

继承 `AbstractEnhancedExtractor`，添加 `@Component` 注解：

```java
@Component
public class YourExtractor extends AbstractEnhancedExtractor {

    // 1. 定义元数据
    @Override
    public ExtractorMetadata getMetadata() {
        return ExtractorMetadata.builder()
            .code("your_code")
            .name("你的提取器名称")
            .description("提取功能描述")
            .category("builtin")
            .outputField("输出字段名")
            .dataType("string")
            .multiValue(true)
            .accuracy("high")
            .performance("fast")
            .version("1.0.0")
            .build();
    }

    // 2. 定义正则表达式
    @Override
    protected List<ExtractorPattern> getPatterns() {
        return Arrays.asList(
            ExtractorPattern.of("pattern1", "正则表达式1", 0.95f),
            ExtractorPattern.of("pattern2", "正则表达式2", 0.85f)
        );
    }

    // 3. 处理匹配结果
    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String value = matcher.group();

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(value)
            .rawValue(value)
            .confidence(0.95f)
            .validation("验证信息")
            .businessMeaning("业务含义说明")
            .build();
    }

    // 4. 提供示例
    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "输入文本示例",
                "[\"期望输出\"]",
                "示例说明"
            )
        );
    }

    // 5. 提供默认选项（可选）
    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("option1", "default_value");
        return options;
    }
}
```

### 步骤2：添加数据库配置（可选）

在 `enhanced_extractors_init.sql` 中添加配置：

```sql
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active)
VALUES (1, '你的提取器名称', 'your_code', '描述', 'builtin', TRUE, TRUE);

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence)
SELECT id, '规则名称', '正则表达式', 100, 0.95
FROM extractor_configs WHERE code = 'your_code';
```

### 步骤3：自动注册

将类放到 `com.datalabeling.service.extraction.impl` 包下，Spring会自动发现并注册到 `ExtractorRegistry`。

## API接口

### 获取所有提取器
```http
GET /api/enhanced-extractors/list
```

### 获取内置提取器
```http
GET /api/enhanced-extractors/builtin
```

### 按分类获取
```http
GET /api/enhanced-extractors/category/{category}
```

### 按标签获取
```http
GET /api/enhanced-extractors/tag/{tag}
```

### 搜索提取器
```http
GET /api/enhanced-extractors/search?keyword=邮箱
```

### 获取提取器详情
```http
GET /api/enhanced-extractors/{code}
```

### 测试提取器
```http
POST /api/enhanced-extractors/{code}/test
Content-Type: application/json

{
  "text": "测试文本",
  "options": {
    "option1": "value1"
  }
}
```

### 获取统计信息
```http
GET /api/enhanced-extractors/stats
```

## 在标签中使用提取器

### 配置示例

```json
{
  "extractorType": "email",
  "fieldName": "联系邮箱",
  "options": {
    "validate_format": true,
    "identify_provider": true
  }
}
```

### 大模型集成

提取结果自动包含 `llmPromptContext` 字段，可直接用于大模型提示词：

```
=== 邮箱地址提取器提取结果 ===
提取器: email
提取说明: 提取各种格式的邮箱地址，识别常见邮箱服务商
数据类型: string
结果数量: 2

结果 #1:
  值: zhangsan@qq.com
  置信度: 98%
  验证: 标准邮箱格式，服务商: QQ邮箱
  业务含义: 用户邮箱地址，用于联系和身份验证
  附加信息:
    username: zhangsan
    domain: qq.com
    provider: QQ邮箱

结果 #2:
  值: john@gmail.com
  置信度: 95%
  ...
```

## 数据库初始化

执行SQL脚本初始化预置提取器配置：

```bash
mysql -u root -p data_labeling < backend/sql/enhanced_extractors_init.sql
```

## 扩展性

系统设计支持以下扩展方式：

1. **新建Java提取器类** - 实现复杂的业务逻辑
2. **自定义数据库配置** - 通过Web界面创建正则提取器
3. **AI辅助生成** - 使用DeepSeek生成正则表达式
4. **复合提取器** - 组合多个提取器

## 性能优化

1. **正则表达式编译缓存** - 避免重复编译
2. **自动去重** - 按字段+值去重，保留高置信度结果
3. **置信度阈值过滤** - 过滤低质量结果
4. **结果分组** - 按字段分组返回

## 安全性

1. **用户级数据隔离** - 每个用户只能访问自己的提取器配置
2. **系统内置保护** - 系统预置提取器不可删除
3. **输入验证** - 选项参数严格验证

## 文件清单

### 后端Java文件
```
backend/src/main/java/com/datalabeling/service/extraction/
├── IEnhancedExtractor.java              # 增强型提取器接口
├── AbstractEnhancedExtractor.java       # 提取器基类
├── ExtractorMetadata.java               # 提取器元数据
├── EnhancedExtractedResult.java         # 增强型提取结果
├── ExtractorExample.java                # 提取器示例
├── ExtractorRegistry.java               # 提取器注册中心
├── EnhancedExtractionOrchestrator.java  # 增强型协调器
├── INumberExtractor.java                # 原有接口
├── ExtractedNumber.java                 # 原有结果类
├── ExtractionOrchestrator.java          # 原有协调器
├── DynamicExtractor.java                # 动态提取器
└── impl/
    ├── EmailExtractor.java              # 邮箱提取器
    ├── DateExtractor.java               # 日期提取器
    ├── MoneyExtractor.java              # 金额提取器
    ├── IpAddressExtractor.java          # IP地址提取器
    ├── UrlExtractor.java                # URL提取器
    ├── CarPlateExtractor.java           # 车牌号提取器
    ├── CompanyNameExtractor.java        # 公司名称提取器
    ├── IdCardExtractor.java             # 身份证提取器（原有）
    ├── PhoneExtractor.java              # 手机号提取器（原有）
    └── BankCardExtractor.java           # 银行卡提取器（原有）

backend/src/main/java/com/datalabeling/controller/
└── EnhancedExtractorController.java     # 增强型提取器API
```

### 数据库文件
```
backend/sql/
└── enhanced_extractors_init.sql         # 预置提取器初始化
```

## 总结

本增强型提取器系统提供了：

1. ✅ **10+ 个预置提取器** - 覆盖常见业务场景
2. ✅ **Java代码驱动** - 逻辑严密、易于扩展
3. ✅ **自动注册发现** - 无需手动配置
4. ✅ **元数据支持** - 完整的描述和示例
5. ✅ **上下文增强** - 业务含义和验证信息
6. ✅ **大模型友好** - 自动生成提示词
7. ✅ **API接口完整** - 支持查询、测试、统计
8. ✅ **数据库初始化** - 一键部署预置提取器

现在你可以：
- 使用现有的预置提取器进行数据提取
- 通过编写Java类创建新的自定义提取器
- 在标签中配置提取器选项
- 将提取结果提交给大模型进行精准分析
