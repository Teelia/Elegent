# -*- coding: utf-8 -*-
"""
验证Bug修复效果的测试脚本
"""
import pandas as pd
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"
df = pd.read_excel(file_path)

def extract_conclusion(text):
    if pd.isna(text):
        return None
    import re
    match = re.search(r'结论[：:]\s*([是否])', str(text))
    return match.group(1) if match else None

df['结论'] = df['涉警当事人信息完整性检查_v1'].apply(extract_conclusion)
yes_records = df[df['结论'] == '是'].copy()

print("=" * 80)
print("Bug修复验证 - 测试28条原本判定为'是'的记录")
print("=" * 80)

test_cases = [
    (162, "身份证号前缀问题", "？340811197505046323"),
    (222, "身份证号前缀问题", "？34030219790910125X"),
    (599, "身份证号前缀问题", "？340321201004100832"),
    (226, "护照号格式问题", "C789056321"),
    (280, "护照号格式问题", "Q00611293"),
]

print("\n【关键测试用例】")
for row_num, desc, target in test_cases:
    row = yes_records[yes_records['行号'] == row_num]
    if not row.empty:
        content = str(row.iloc[0]['反馈内容'])
        found = target in content
        print(f"\n行号 {row_num} ({desc}):")
        print(f"  目标: {target}")
        print(f"  原始判定: 是")
        print(f"  包含目标: {'✓ 是' if found else '✗ 否'}")
        print(f"  预期: 修复后应判定为'否'")

print("\n" + "=" * 80)
print("修复验证说明")
print("=" * 80)
print("""
修复后的验证逻辑应该能检测到：

1. 身份证号前缀问题（Bug 1修复）
   - 检测逻辑：IdCardLengthValidator.extractAndValidate()
   - 检测：？340811197505046323（19位，前缀有问号）

2. 可疑护照号问题（Bug 3+4修复）
   - 检测逻辑：PassportValidator.detectSuspiciousPassports()
   - 检测：C789056321、Q00611293（9位，非标准格式）

3. 错误位数身份证号（新增功能）
   - 检测逻辑：IdCardLengthValidator.detectInvalidLengthIdCards()
   - 检测：14/16/17/19/20/21位的数字序列

建议：重新运行分析任务，检查这28条记录是否被正确判定为'否'
""")
