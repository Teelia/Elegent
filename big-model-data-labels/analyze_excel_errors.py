# -*- coding: utf-8 -*-
import pandas as pd
import re
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def validate_id_number(id_str):
    """验证身份证号格式是否正确"""
    if not id_str:
        return False, "空值"
    # 移除空格
    id_str = id_str.strip()
    # 检查是否为18位
    if len(id_str) != 18:
        return False, f"长度错误({len(id_str)}位)"
    # 检查前17位是否为数字
    if not id_str[:17].isdigit():
        return False, "前17位非全数字"
    # 检查第18位是否为数字或X
    if not (id_str[17].isdigit() or id_str[17].upper() == 'X'):
        return False, "第18位格式错误"
    return True, "格式正确"

file_path = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-74-results-with-reasoning.xlsx'
df = pd.read_excel(file_path)

label_col = '涉警当事人信息完整性检查_v1'
yes_mask = df[label_col].astype(str).str.contains('结论：是', na=False)
yes_df = df[yes_mask]

print(f'=== 共 {len(yes_df)} 条结论为"是"的数据 ===\n')

# 身份证号正则模式
id_pattern = r'\d{15,19}[\dXx]|\d{14,17}[\dXx](?!\d)'

for idx, row in yes_df.iterrows():
    row_num = row['行号']
    content = str(row['反馈内容'])
    result = str(row[label_col])

    print(f"{'='*100}")
    print(f"【行号 {row_num}】大模型结论：是")
    print(f"大模型判断依据：{result.split('判断依据：')[1].split('[二次强化]')[0].strip()}")
    print(f"\n原文内容：")
    print(content)

    # 提取所有身份证号
    id_numbers = re.findall(r'[\dXx]{15,19}', content)
    print(f"\n提取到的身份证号：")

    errors_found = []
    valid_count = 0
    invalid_count = 0

    for i, id_num in enumerate(id_numbers, 1):
        is_valid, reason = validate_id_number(id_num)
        if is_valid:
            valid_count += 1
            print(f"  {i}. {id_num} ✓ [{reason}]")
        else:
            invalid_count += 1
            errors_found.append((id_num, reason))
            print(f"  {i}. {id_num} ✗ [{reason}]")

    # 分析错误
    print(f"\n【问题分析】")
    if errors_found:
        print(f"❌ 发现 {len(errors_found)} 个格式错误的身份证号：")
        for id_num, reason in errors_found:
            print(f"   - {id_num}: {reason}")
        print(f"\n结论：根据标签定义，存在任一涉警当事人身份证号格式错误应判定为【否】，但大模型判定为【是】")
    else:
        # 检查是否有遗漏的涉警当事人
        print(f"⚠️ 所有身份证号格式均正确，需要检查是否有遗漏的涉警当事人")
        # 检查是否有遗漏的涉警当事人(简化处理)
        if "未提供" in content or "未登记" in content or "拒绝" in content:
            print(f"   ⚠️ 文本中存在'未提供/未登记/拒绝'等关键词，可能存在信息缺失当事人")

    print()
