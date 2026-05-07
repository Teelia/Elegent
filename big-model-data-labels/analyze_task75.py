# -*- coding: utf-8 -*-
import pandas as pd
import re
import json

# 读取Excel文件
file_path = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-75-results-with-reasoning.xlsx'
df = pd.read_excel(file_path)

# 分析最后一列的结论
last_col = '在校学生信息完整性检查_v1'

# 提取结论和推理
def parse_result(text):
    if pd.isna(text):
        return None, None
    # 提取结论：是或否
    conclusion_match = re.search(r'结论[：:]\s*[是否]', text)
    if conclusion_match:
        conclusion = conclusion_match.group().replace('结论', '').replace('：', '').replace(':', '').strip()
    else:
        conclusion = None

    # 提取推理内容
    reasoning_match = re.search(r'推理[：:]\s*(.+)', text, re.DOTALL)
    if reasoning_match:
        reasoning = reasoning_match.group(1).strip()
    else:
        reasoning = text

    return conclusion, reasoning

# 统计
yes_count = 0
no_count = 0
yes_rows = []

print("=" * 80)
print("在校学生信息完整性检查v1 - 数据分析报告")
print("=" * 80)

for idx, row in df.iterrows():
    result_text = row[last_col]
    conclusion, reasoning = parse_result(result_text)

    if conclusion == '是':
        yes_count += 1
        yes_rows.append({
            'row_idx': idx,
            'original_data': row.iloc[1:-1].to_dict(),  # 排除行号和结果列
            'reasoning': reasoning
        })
    elif conclusion == '否':
        no_count += 1

print(f"\n总行数: {len(df)}")
print(f"结论为'是': {yes_count}")
print(f"结论为'否': {no_count}")
print(f"结论为'是'的占比: {yes_count/len(df)*100:.2f}%")

print("\n" + "=" * 80)
print("结论为'是'的数据详细分析（前20条）")
print("=" * 80)

for i, item in enumerate(yes_rows[:20]):
    print(f"\n【第{item['row_idx']+1}行】")
    print(f"推理: {item['reasoning'][:200]}...")

    # 提取关键信息
    data = item['original_data']
    print("原始数据:")
    for key, value in data.items():
        if pd.notna(value) and str(value).strip():
            print(f"  {key}: {value}")

print("\n" + "=" * 80)
print("错误原因分析")
print("=" * 80)

# 分析可能存在的错误模式
print("\n根据标签定义，结论为'是'的情况只有两种：")
print("1. 不涉及在校学生")
print("2. 涉及在校学生且信息完整（6项：姓名、身份证号、学校全称、在读年级、院系/专业、联系方式）")
print("\n如果这44条数据结论为'是'但实际应该是'否'，可能的原因：")

print("\n【可能错误模式1】模型错误判断为'不涉及学生'")
print("  - 实际涉及学生，但模型未识别出学生身份")
print("  - 关键词识别问题（如：'学员'、'学生'等词汇未被正确识别）")

print("\n【可能错误模式2】信息完整性判断错误")
print("  - 实际信息不完整，但模型判断为完整")
print("  - 缺少联系方式、院系/专业等关键信息未被检测到")

print("\n【可能错误模式3】数据解析问题")
print("  - 原始数据列数较多，信息分散在不同列")
print("  - 模型未综合所有列的信息")

# 保存详细分析到文件
output_file = r'D:\01_代码库\大模型自动化数据标签\task75_analysis.txt'
with open(output_file, 'w', encoding='utf-8') as f:
    f.write("=" * 80 + "\n")
    f.write("在校学生信息完整性检查v1 - 错误数据分析\n")
    f.write("=" * 80 + "\n\n")

    f.write(f"总行数: {len(df)}\n")
    f.write(f"结论为'是': {yes_count}\n")
    f.write(f"结论为'否': {no_count}\n\n")

    f.write("=" * 80 + "\n")
    f.write("所有结论为'是'的数据详情\n")
    f.write("=" * 80 + "\n\n")

    for i, item in enumerate(yes_rows):
        f.write(f"\n【第{i+1}条 - 第{item['row_idx']+1}行】\n")
        f.write(f"推理: {item['reasoning']}\n\n")
        f.write("原始数据:\n")
        for key, value in item['original_data'].items():
            if pd.notna(value) and str(value).strip():
                f.write(f"  {key}: {value}\n")
        f.write("-" * 80 + "\n")

print(f"\n详细分析已保存到: {output_file}")
