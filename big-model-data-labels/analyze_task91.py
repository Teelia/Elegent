# -*- coding: utf-8 -*-
import pandas as pd
import json

# 读取Excel文件
file_path = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-91-results-with-reasoning.xlsx'
df = pd.read_excel(file_path)

print(f"总行数: {len(df)}")
print(f"\n列名: {df.columns.tolist()}")

# 分析标签结果分布
label_col = '涉警当事人信息完整性检查_v1'
print(f"\n=== {label_col} 结果分布 ===")
value_counts = df[label_col].value_counts()
print(value_counts)

# 统计"是"和"否"的数量
yes_count = (df[label_col] == "是").sum()
no_count = (df[label_col] == "否").sum()
print(f"\n是: {yes_count} ({yes_count/len(df)*100:.1f}%)")
print(f"否: {no_count} ({no_count/len(df)*100:.1f}%)")

# 分析推理内容，提取关键模式
print("\n=== 分析推理模式 ===")

# 提取所有推理内容
reasoning_list = []
for idx, row in df.head(50).iterrows():  # 先看前50条
    reasoning = row.get('推理内容', '')
    result = row.get(label_col, '')
    reasoning_list.append({
        'index': idx,
        'result': result,
        'reasoning': str(reasoning)[:200]  # 截取前200字符
    })

# 查找判断为"是"但推理中提到"格式错误"的矛盾案例
print("\n=== 检测矛盾案例（结果=是 但推理提到格式错误）===")
conflicts = []
for idx, row in df.iterrows():
    result = row.get(label_col, '')
    reasoning = str(row.get('推理内容', ''))
    if result == "是" and ('格式错误' in reasoning or '格式不正确' in reasoning or '身份证号格式' in reasoning):
        conflicts.append({
            'index': idx,
            'row_number': row.get('序号', idx),
            'reasoning': reasoning[:300]
        })

print(f"发现 {len(conflicts)} 个矛盾案例")
for i, c in enumerate(conflicts[:10]):  # 显示前10个
    print(f"\n--- 矛盾案例 {i+1} (行号: {c['row_number']}) ---")
    print(c['reasoning'])

# 分析推理中的错误模式
print("\n=== 分析常见错误模式 ===")

# 统计出现"身份证号格式"相关的推理
pattern_errors = 0
for idx, row in df.iterrows():
    reasoning = str(row.get('推理内容', ''))
    if '身份证号格式' in reasoning and '前6位是1位前缀字符' in reasoning:
        pattern_errors += 1

print(f"出现'身份证号前6位是1位前缀字符'这种错误推理的数量: {pattern_errors}")

# 检查具体的身份证号提取错误
print("\n=== 检查身份证号提取错误案例 ===")
id_extraction_errors = []
for idx, row in df.head(20).iterrows():
    reasoning = str(row.get('推理内容', ''))
    # 查找类似 "340200202601012344 (原本: 身份证号前6位是1位前缀字符" 的模式
    if '前6位是1位前缀字符' in reasoning:
        # 提取警情内容
        alert_content = str(row.get('警情内容', ''))[:100]
        id_extraction_errors.append({
            'index': idx,
            'row_number': row.get('序号', idx),
            'alert_preview': alert_content,
            'reasoning_snippet': reasoning[reasoning.find('身份证号格式'):reasoning.find('身份证号格式')+100] if '身份证号格式' in reasoning else reasoning[:200]
        })

for i, e in enumerate(id_extraction_errors[:5]):
    print(f"\n--- 案例 {i+1} (行号: {e['row_number']}) ---")
    print(f"警情预览: {e['alert_preview']}")
    print(f"推理片段: {e['reasoning_snippet']}")

# 分析原文数据中的身份证号格式
print("\n=== 分析原文身份证号格式 ===")
sample_data = df.iloc[0]['警情内容'] if len(df) > 0 else ""
print(f"第一条数据预览: {sample_data[:300]}")
