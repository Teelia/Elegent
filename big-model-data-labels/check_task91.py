# -*- coding: utf-8 -*-
import pandas as pd
import sys
import io

# 设置stdout编码为utf-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 读取Excel
df = pd.read_excel(r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-91-results-with-reasoning.xlsx')

print("列名:")
for i, col in enumerate(df.columns):
    print(f"  {i}: {col}")

print(f"\n总行数: {len(df)}")

# 查看标签列（第5列，索引为5）
label_col_idx = 5
label_col_name = df.columns[label_col_idx]

print(f"\n标签列名: {label_col_name}")

# 查看前3条数据的标签列值
print("\n=== 前3条标签列值 ===")
for i in range(min(3, len(df))):
    val = df.iloc[i, label_col_idx]
    print(f"\n--- 第{i}行 ---")
    print(f"值类型: {type(val)}")
    print(f"值长度: {len(str(val))}")
    print(f"值内容: {str(val)[:300]}")

# 统计唯一值数量
unique_vals = df.iloc[:, label_col_idx].nunique()
print(f"\n唯一值数量: {unique_vals}")

# 检查是否包含"是"或"否"
yes_count = df.iloc[:, label_col_idx].astype(str).str.contains("^(是|否)$", regex=True).sum()
print(f"纯'是'或'否'的数量: {yes_count}")

# 查看值的前20个字符分布
print("\n=== 前20个字符分布（前10个不同值）===")
first_chars = df.iloc[:, label_col_idx].astype(str).str[:20]
unique_first = first_chars.unique()
for i, val in enumerate(unique_first[:10]):
    count = (first_chars == val).sum()
    print(f"\n{i+1}. (出现{count}次)")
    print(f"   {val}")
