# -*- coding: utf-8 -*-
"""
分析涉警当事人信息完整性检查的错误判定
"""
import pandas as pd
import re
import json

# 读取Excel文件
file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"
df = pd.read_excel(file_path)

print("=" * 80)
print("涉警当事人信息完整性检查_v1 - 错误判定分析")
print("=" * 80)
print(f"\n总数据行数: {len(df)}")

# 找出标签列为"是"的所有记录
label_col = None
for col in df.columns:
    if '涉警当事人' in str(col) or '信息完整性' in str(col):
        label_col = col
        break

if label_col is None:
    # 查找包含 _v1 的列
    for col in df.columns:
        if '_v1' in str(col):
            label_col = col
            break

print(f"\n标签列名: {label_col}")

# 筛选出结论为"是"的记录
yes_records = df[df[label_col] == '是'].copy()
print(f"\n结论为'是'的记录数: {len(yes_records)}")
print(f"用户指出错误数量: 28条")

# 检查可能的错误原因
print("\n" + "=" * 80)
print("开始逐条分析结论为'是'的记录...")
print("=" * 80)

error_patterns = {
    '撤警关键词': ['撤警', '无效警情', '无事实', '误报', '重复警情', '未发现报警情况'],
    '身份证缺失': ['无身份证', '未提供身份证', '身份证缺失', '身份证不详'],
    '格式错误': ['身份证号码格式', '身份证格式有误']
}

analyzed_results = []

for idx, row in yes_records.iterrows():
    print(f"\n{'='*80}")
    print(f"记录 #{idx + 1}")
    print(f"{'='*80}")

    # 找到内容列（通常是第一列或名为"内容"/"警情信息"等）
    content_col = None
    for col in df.columns:
        if '内容' in str(col) or '警情' in str(col) or '事实' in str(col):
            content_col = col
            break

    if content_col is None:
        # 取第一列作为内容列
        content_col = df.columns[0]

    content = str(row[content_col])
    reasoning = row.get('推理过程', row.get('reasoning', ''))

    print(f"\n【原始内容】:\n{content[:500]}")  # 限制长度

    if pd.notna(reasoning):
        print(f"\n【推理过程】:\n{reasoning[:500]}")

    # 分析可能的错误原因
    print(f"\n【错误原因分析】:")

    issues_found = []

    # 1. 检查是否包含撤警关键词（这是合格条件，不应该算错误）
    has_cancellation = False
    for keyword in error_patterns['撤警关键词']:
        if keyword in content:
            has_cancellation = True
            issues_found.append(f"✓ 包含撤警关键词'{keyword}'（直接合格）")
            break

    # 2. 检查身份证号情况
    # 查找所有身份证号模式
    id_pattern = r'\d{15}|\d{17}[\dXx]|\d{18}'
    id_numbers = re.findall(id_pattern, content)

    # 查找身份证关键词
    id_keywords = ['身份证', '身份证号', '证件号码', '身份证明']
    has_id_mention = any(kw in content for kw in id_keywords)

    # 查找人名（通常后面跟着身份证）
    # 中文姓名模式：2-4个汉字
    name_pattern = r'[\u4e00-\u9fa5]{2,4}'
    names = re.findall(name_pattern, content)

    print(f"  - 发现的身份证号: {len(id_numbers)}个")
    if id_numbers:
        print(f"    {id_numbers[:5]}")  # 显示前5个

    print(f"  - 提到身份证相关: {'是' if has_id_mention else '否'}")
    print(f"  - 发现中文姓名: {len(set(names))}个唯一姓名")

    # 检查是否有姓名但无身份证的情况
    if len(names) > 0 and len(id_numbers) == 0:
        issues_found.append("✗ 有姓名但无身份证号")

    # 检查身份证号格式
    valid_ids = []
    invalid_ids = []
    for id_num in id_numbers:
        if len(id_num) == 18:
            valid_ids.append(id_num)
        elif len(id_num) in [15, 17]:
            invalid_ids.append(id_num)

    if invalid_ids:
        issues_found.append(f"✗ 发现{len(invalid_ids)}个格式不完整的身份证号")

    # 3. 检查涉警当事人描述
    party_keywords = ['纠纷', '当事人', '双方', '受害人', '侵权人', '嫌疑人', '违法人']
    has_party = any(kw in content for kw in party_keywords)

    if has_party and len(valid_ids) == 0 and not has_cancellation:
        issues_found.append("✗ 提到涉警当事人但无有效身份证号")

    for issue in issues_found:
        print(f"  {issue}")

    # 判断这条记录是否应该为"否"
    should_be_no = (
        not has_cancellation and  # 没有撤警关键词
        (len(names) > 0 or has_party) and  # 有当事人
        len(valid_ids) == 0  # 没有有效身份证号
    )

    if should_be_no:
        print(f"\n⚠️ 【结论】：这条记录应该判定为'否'")

    analyzed_results.append({
        'index': idx,
        'content': content[:200],
        'should_be_no': should_be_no,
        'has_cancellation': has_cancellation,
        'has_valid_id': len(valid_ids) > 0,
        'has_party_mention': has_party,
        'issues': issues_found
    })

# 统计结果
print("\n" + "=" * 80)
print("【总体统计】")
print("=" * 80)

should_be_no_count = sum(1 for r in analyzed_results if r['should_be_no'])
print(f"\n应该判定为'否'但被判定为'是'的记录数: {should_be_no_count}")
print(f"用户报告的错误数量: 28条")

if should_be_no_count > 0:
    print("\n【主要错误原因分类】:")

    # 按错误原因分类
    no_id_with_name = sum(1 for r in analyzed_results
                          if r['should_be_no'] and '有姓名但无身份证号' in str(r['issues']))
    party_no_id = sum(1 for r in analyzed_results
                     if r['should_be_no'] and '提到涉警当事人但无有效身份证号' in str(r['issues']))
    invalid_format = sum(1 for r in analyzed_results
                        if r['should_be_no'] and '格式不完整的身份证号' in str(r['issues']))

    print(f"  1. 有姓名但无身份证号: {no_id_with_name}条")
    print(f"  2. 提到涉警当事人但无有效身份证号: {party_no_id}条")
    print(f"  3. 身份证号格式不完整: {invalid_format}条")

# 保存详细分析结果到文件
output_file = r"D:\01_代码库\大模型自动化数据标签\error_analysis_result.txt"
with open(output_file, 'w', encoding='utf-8') as f:
    f.write("涉警当事人信息完整性检查_v1 - 错误判定详细分析\n")
    f.write("=" * 80 + "\n\n")

    for i, result in enumerate(analyzed_results):
        if result['should_be_no']:
            f.write(f"\n错误记录 #{i+1}\n")
            f.write("-" * 40 + "\n")
            f.write(f"内容: {result['content']}\n")
            f.write(f"问题: {result['issues']}\n")
            f.write(f"应该判定为: 否\n\n")

print(f"\n详细分析结果已保存到: {output_file}")
