# -*- coding: utf-8 -*-
"""
检查Excel文件结构
"""
import pandas as pd
import sys
import io

# 设置stdout编码为utf-8
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"

print("=" * 80)
print("Excel文件结构检查")
print("=" * 80)

# 读取Excel文件
df = pd.read_excel(file_path)

print(f"\n总行数: {len(df)}")
print(f"总列数: {len(df.columns)}")

print("\n所有列名:")
for i, col in enumerate(df.columns):
    print(f"  {i+1}. {repr(col)}")

# 显示前5行数据
print("\n前5行数据样本:")
print(df.head().to_string())

# 检查每一列的数据类型和样本值
print("\n\n各列数据统计:")
for col in df.columns:
    print(f"\n列名: {repr(col)}")
    print(f"  数据类型: {df[col].dtype}")
    print(f"  唯一值数量: {df[col].nunique()}")

    if df[col].nunique() <= 20:
        print(f"  唯一值: {list(df[col].unique())}")
    else:
        value_counts = df[col].value_counts()
        print(f"  前10个常见值:")
        for val, count in value_counts.head(10).items():
            print(f"    {repr(val)}: {count}次")
