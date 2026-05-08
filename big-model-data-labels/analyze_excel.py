# -*- coding: utf-8 -*-
import pandas as pd
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

file_path = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-80-results-with-reasoning.xlsx'
df = pd.read_excel(file_path)

print('=== 数据概览 ===')
print(f'数据行数: {len(df)}')
print(f'列名: {df.columns.tolist()}')

# 查看该列的唯一值
label_col = '涉警当事人信息完整性检查_v1'
print(f'\n=== "{label_col}" 列的唯一值 ===')
unique_values = df[label_col].unique()
print(f'唯一值数量: {len(unique_values)}')
for val in unique_values[:20]:
    print(f'  - 类型: {type(val).__name__}, 值: {repr(val)[:200]}')

# 统计各种值
print(f'\n=== 值统计 ===')
value_counts = df[label_col].value_counts()
print(value_counts)

# 查找包含"是"的数据行
print(f'\n=== 查找包含"是"的数据 ===')
yes_mask = df[label_col].astype(str).str.contains('结论：是', na=False)
print(f'结论为"是"的行数: {yes_mask.sum()}')

if yes_mask.sum() > 0:
    print('\n所有结论为"是"的数据:')
    yes_df = df[yes_mask]
    for idx in yes_df.index:
        row = df.loc[idx]
        print(f'\n--- 行索引 {idx} ---')
        content_col = '警情内容' if '警情内容' in df.columns else df.columns[1]
        print(f'{content_col}: {row[content_col]}')
        print(f'标签值: {row[label_col]}')
        print('='*100)
