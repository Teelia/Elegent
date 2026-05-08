# -*- coding: utf-8 -*-
"""
验证Bug修复效果 - 直接测试28条错误记录
"""
import pandas as pd
import sys
import io

# 设置Java路径和类路径
import subprocess
import json

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"
df = pd.read_excel(file_path)

def extract_conclusion(text):
    import re
    if pd.isna(text):
        return None
    match = re.search(r'结论[：:]\s*([是否])', str(text))
    return match.group(1) if match else None

df['结论'] = df['涉警当事人信息完整性检查_v1'].apply(extract_conclusion)
yes_records = df[df['结论'] == '是'].copy()

print("=" * 100)
print("验证Bug修复效果 - 测试28条原本判定为'是'的记录")
print("=" * 100)

# 关键测试用例
test_cases = [
    (162, "前缀字符", "？340811197505046323", "身份证号前有问号"),
    (222, "前缀字符", "？34030219790910125X", "身份证号前有问号"),
    (599, "前缀字符", "？340321201004100832", "身份证号前有问号"),
    (226, "护照号格式", "C789056321", "9位非标准护照号"),
    (280, "护照号格式", "Q00611293", "非标准前缀护照号"),
]

print("\n【关键测试用例验证】")
print("-" * 100)

for row_num, error_type, target, description in test_cases:
    row = yes_records[yes_records['行号'] == row_num]
    if row.empty:
        print(f"行号 {row_num}: 未找到记录")
        continue

    content = str(row.iloc[0]['反馈内容'])
    contains = target in content

    print(f"\n行号 {row_num} - {error_type}")
    print(f"  描述: {description}")
    print(f"  目标: {target}")
    print(f"  原始判定: 是")
    print(f"  文中包含: {'✓ 是' if contains else '✗ 否'}")

    if not contains:
        print(f"  ⚠️ 警告: 文本中未找到目标值，可能需要检查实际内容")

# 创建Java验证程序
java_code = '''
import com.datalabeling.util.*;
import java.util.*;

public class QuickValidation {
    public static void main(String[] args) {
        PostProcessValidator validator = new PostProcessValidator(
            new com.datalabeling.service.extraction.PartyExtractor());

        // 测试用例1：前缀字符
        String test1 = "鲍克娥（？340811197505046323，13866603854）";
        Map<String, Object> data1 = new HashMap<>();
        data1.put("content", test1);
        PostProcessValidator.ValidationResult result1 = validator.validate("是", data1, null);
        System.out.println("测试1 - 前缀检测: " + result1.isValid() + " - " + result1.getMessage());

        // 测试用例2：可疑护照号
        String test2 = "顾晨，C789056321,无其他信息";
        Map<String, Object> data2 = new HashMap<>();
        data2.put("content", test2);
        PostProcessValidator.ValidationResult result2 = validator.validate("是", data2, null);
        System.out.println("测试2 - 可疑护照: " + result2.isValid() + " - " + result2.getMessage());

        // 测试用例3：错误位数
        String test3 = "身份证号：12345678901234";
        IdCardLengthValidator.InvalidLengthDetection result3 =
            IdCardLengthValidator.detectInvalidLengthIdCards(test3);
        System.out.println("测试3 - 错误位数: " + result3.hasInvalid() + " - " + result3.getSummary());
    }
}
'''

print("\n" + "=" * 100)
print("创建Java验证程序")
print("=" * 100)

# 写入Java文件
java_file = r"D:\01_代码库\大模型自动化数据标签\backend\src\test\java\com\datalabeling\QuickValidation.java"
with open(java_file, 'w', encoding='utf-8') as f:
    f.write('''package com.datalabeling;

import com.datalabeling.util.*;
import com.datalabeling.service.extraction.PartyExtractor;
import java.util.*;

public class QuickValidation {
    public static void main(String[] args) {
        PostProcessValidator validator = new PostProcessValidator(new PartyExtractor());

        // 测试用例1：前缀字符 - 行号222
        String test1 = "鲍克娥（？34030219790910125X，13866603854）";
        Map<String, Object> data1 = new HashMap<>();
        data1.put("content", test1);
        PostProcessValidator.ValidationResult result1 = validator.validate("是", data1, null);
        System.out.println("测试1 - 前缀检测: valid=" + result1.isValid());
        System.out.println("         message=" + result1.getMessage());

        // 测试用例2：可疑护照号 - 行号226
        String test2 = "顾晨，C789056321,无其他信息";
        Map<String, Object> data2 = new HashMap<>();
        data2.put("content", test2);
        PostProcessValidator.ValidationResult result2 = validator.validate("是", data2, null);
        System.out.println("测试2 - 可疑护照: valid=" + result2.isValid());
        System.out.println("         message=" + result2.getMessage());

        // 测试用例3：错误位数
        IdCardLengthValidator.InvalidLengthDetection result3 =
            IdCardLengthValidator.detectInvalidLengthIdCards("身份证号12345678901234");
        System.out.println("测试3 - 错误位数(14位): hasInvalid=" + result3.hasInvalid());
        System.out.println("         summary=" + result3.getSummary());

        // 测试用例4：行号162完整场景
        String test4 = "我单位[吴烨080244]赶到现场。经查:朱雪飞(342921196402195014)系环卫工人，" +
                    "其妻子何月桂（342830196708171665）路过鲍克娥（？340811197505046323）家店铺时，" +
                    "看到鲍克娥在扔垃圾，认为鲍克娥经营的批发超市扔的垃圾太多";
        Map<String, Object> data4 = new HashMap<>();
        data4.put("content", test4);
        PostProcessValidator.ValidationResult result4 = validator.validate("是", data4, null);
        System.out.println("测试4 - 行号162场景: valid=" + result4.isValid());
        System.out.println("         message=" + result4.getMessage());

        System.out.println("\\n=== 验证完成 ===");
        System.out.println("预期结果: 所有测试的valid都应该为false（判定为'否'）");
    }
}
''')

print(f"Java验证程序已创建: {java_file}")
print("\n编译并运行验证程序...")

# 编译并运行
try:
    # 编译
    compile_result = subprocess.run(
        ['mvn', 'compile', '-q', '-DskipTests'],
        cwd=r"D:\01_代码库\大模型自动化数据标签\backend",
        capture_output=True,
        text=True,
        timeout=60
    )

    if compile_result.returncode == 0:
        print("编译成功")

        # 运行验证程序
        run_result = subprocess.run(
            ['mvn', 'exec:java', '-q', '-Dexec.mainClass="com.datalabeling.QuickValidation"'],
            cwd=r"D:\01_代码库\大模型自动化数据标签\backend",
            capture_output=True,
            text=True,
            timeout=60
        )

        print("\n验证结果:")
        print("-" * 50)
        # 提取关键输出行
        for line in run_result.stdout.split('\n'):
            if '测试' in line or 'valid' in line or 'message' in line or 'summary' in line or '完成' in line:
                print(line)
    else:
        print("编译失败")
        print(compile_result.stdout[-500:] if len(compile_result.stdout) > 500 else compile_result.stdout)

except Exception as e:
    print(f"执行失败: {e}")

print("\n" + "=" * 100)
print("验证说明")
print("=" * 100)
print("""
修复后的验证逻辑应该能检测到：

1. ✅ 身份证号前缀问题 (Bug 1修复)
   - 检测到 ？340811197505046323 (19位，包含问号)
   - 预期：valid=false，判定为'否'

2. ✅ 可疑护照号问题 (Bug 3+4修复)
   - 检测到 C789056321 (9位数字)
   - 预期：valid=false，判定为'否'

3. ✅ 错误位数身份证号 (新增功能)
   - 检测到 12345678901234 (14位)
   - 预期：hasInvalid=true

4. ✅ 多人当事人信息不完整 (PartyExtractor)
   - 检测到3个当事人但只有2个有效身份证
   - 预期：valid=false，判定为'否'

建议：
1. 检查上述验证程序的输出
2. 如果所有 valid=false，说明修复成功
3. 重新运行完整的分析任务 task-78 验证所有记录
""")
