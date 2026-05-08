# -*- coding: utf-8 -*-
"""
分析涉警当事人信息完整性检查的错误判定 - 修正版
"""
import pandas as pd
import re
import sys
import io

# 设置stdout编码
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 读取Excel文件
file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"
df = pd.read_excel(file_path)

print("=" * 100)
print("涉警当事人信息完整性检查_v1 - 错误判定分析报告")
print("=" * 100)
print(f"\n总数据行数: {len(df)}")

# 从结果列中提取结论
def extract_conclusion(text):
    """从结果文本中提取结论（是/否）"""
    if pd.isna(text):
        return None

    text_str = str(text)

    # 匹配 "结论：是" 或 "结论：否"
    match = re.search(r'结论[：:]\s*([是否])', text_str)
    if match:
        return match.group(1)

    return None

# 提取所有结论
df['结论'] = df['涉警当事人信息完整性检查_v1'].apply(extract_conclusion)

# 统计
conclusion_counts = df['结论'].value_counts()
print(f"\n结论分布:")
for conclusion, count in conclusion_counts.items():
    print(f"  {conclusion}: {count}条")

# 筛选出结论为"是"但可能有错误的记录
yes_records = df[df['结论'] == '是'].copy()
print(f"\n结论为'是'的记录数: {len(yes_records)}")
print(f"用户报告错误数量: 28条")

# 分析每一条"是"的记录
print("\n" + "=" * 100)
print("开始逐条分析结论为'是'的记录...")
print("=" * 100)

errors_found = []

for idx, row in yes_records.iterrows():
    row_num = row['行号']
    content = str(row['反馈内容'])
    full_result = str(row['涉警当事人信息完整性检查_v1'])

    print(f"\n{'='*100}")
    print(f"记录 #行号{row_num}")
    print(f"{'='*100}")

    # 显示原始内容
    print(f"\n【反馈内容】:")
    print(f"{content[:600]}...")

    # 显示原始判定结果
    print(f"\n【原始判定结果】:")
    print(f"{full_result[:500]}...")

    # 分析错误原因
    print(f"\n【详细错误分析】:")

    issues = []

    # 1. 检查撤警关键词（这是合格条件）
    cancellation_keywords = ['撤警', '无效警情', '无事实', '误报', '重复警情', '未发现报警情况', '请求撤警']
    has_cancellation = any(kw in content for kw in cancellation_keywords)
    if has_cancellation:
        print(f"  ✓ 包含撤警关键词 - 判定为'是'是正确的")
        issues.append('有撤警关键词')

    # 2. 查找身份证号
    id_pattern = r'\d{15}|\d{17}[\dXx]|\d{18}'
    id_numbers = re.findall(id_pattern, content)

    # 检查身份证号有效性（18位）
    valid_18_digit_ids = [id_num for id_num in id_numbers if len(id_num) == 18]
    invalid_ids = [id_num for id_num in id_numbers if len(id_num) not in [15, 18]]

    print(f"  - 发现的身份证号数量: {len(id_numbers)}个")
    print(f"    其中18位有效: {len(valid_18_digit_ids)}个")
    if invalid_ids:
        print(f"    格式不完整: {len(invalid_ids)}个 - {invalid_ids[:5]}")

    # 3. 查找姓名
    name_pattern = r'[\u4e00-\u9fa5]{2,4}(?:[（\(]\s*(?:男|女)\s*[）\)])?'
    names = re.findall(name_pattern, content)

    # 过滤掉明显不是姓名的词
    exclude_words = {'报警人', '民警', '辅警', '处警', '出警', '系', '因', '称', '与', '双方', '当事人',
                     '纠纷', '受害人', '嫌疑人', '现场', '经了解', '经查', '电话', '手机', '身份证',
                     '号码', '号码：', '联系', '现住', '户籍', '住址', '业主', '工作人员', '居民',
                     '有限公司', '公司', '行政村', '自然村', '村民', '群众', '路', '室', '号', '栋',
                     '组', '村', '镇', '县', '市', '省', '区', '该', '其', '对方', '本', '无', '未',
                     '有', '到', '了', '在', '系', '因', '后', '前', '将', '对', '和', '或', '及'}

    # 提取可能的人名（2-3个汉字的词，排除常见词）
    possible_names = []
    for name in names:
        # 清理姓名中的括号内容
        clean_name = re.sub(r'[（\(].*?[）\)]', '', name).strip()
        if 2 <= len(clean_name) <= 3 and clean_name not in exclude_words:
            # 检查前后是否有身份相关词汇
            possible_names.append(clean_name)

    print(f"  - 提取的可能涉警当事人姓名: {len(set(possible_names))}个")
    if len(set(possible_names)) <= 10:
        print(f"    {list(set(possible_names))}")

    # 4. 检查身份缺失标记
    missing_keywords = ['未提供身份证', '无身份证', '身份证缺失', '记不清身份证', '身份证记不住',
                        '拒绝登记身份', '拒绝登记信息', '未提供身份', '无身份信息', '身份证号不详',
                        '研判无果', '无果', '未掌握', '无法核实']
    has_missing_mark = any(kw in content for kw in missing_keywords)

    print(f"  - 存在身份缺失标记: {'是' if has_missing_mark else '否'}")
    if has_missing_mark:
        found_kw = [kw for kw in missing_keywords if kw in content]
        print(f"    发现的关键词: {found_kw[:5]}")
        issues.append('有身份缺失标记')

    # 5. 判断是否应该为"否"
    should_be_no = (
        not has_cancellation and  # 没有撤警关键词
        len(possible_names) > 0 and  # 有涉警当事人
        len(valid_18_digit_ids) < len(possible_names)  # 身份证数量少于人数
    )

    # 特殊情况：明确提到"研判无果"等缺失标记
    if has_missing_mark and not has_cancellation:
        should_be_no = True
        issues.append('明确身份缺失但判定为是')

    # 特殊情况：身份证号格式错误
    if invalid_ids and not has_cancellation:
        should_be_no = True
        issues.append(f'存在{len(invalid_ids)}个格式错误身份证号')

    if should_be_no:
        print(f"\n  ⚠️ 【判定错误】这条记录应该判定为'否'")
        errors_found.append({
            'row': row_num,
            'reason': issues,
            'content': content[:200],
            'full_result': full_result
        })
    else:
        print(f"\n  ✓ 【判定正确】这条记录判定为'是'是合理的")

# 统计结果
print("\n" + "=" * 100)
print("【错误统计分析】")
print("=" * 100)

print(f"\n应该判定为'否'但被判定为'是'的记录数: {len(errors_found)}")
print(f"用户报告的错误数量: 28条")
print(f"误差: {abs(len(errors_found) - 28)}条")

if len(errors_found) > 0:
    print("\n【主要错误原因分类】:")

    # 统计错误原因
    reason_counts = {}
    for error in errors_found:
        for reason in error['reason']:
            reason_counts[reason] = reason_counts.get(reason, 0) + 1

    for reason, count in sorted(reason_counts.items(), key=lambda x: -x[1]):
        print(f"  • {reason}: {count}条")

    # 保存详细错误记录
    output_file = r"D:\01_代码库\大模型自动化数据标签\error_records_detail.txt"
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("=" * 100 + "\n")
        f.write("应该判定为'否'但被判定为'是'的错误记录详细列表\n")
        f.write("=" * 100 + "\n\n")

        for i, error in enumerate(errors_found, 1):
            f.write(f"\n错误记录 #{i} (行号: {error['row']})\n")
            f.write("-" * 80 + "\n")
            f.write(f"反馈内容: {error['content']}\n\n")
            f.write(f"错误原因: {error['reason']}\n\n")
            f.write(f"原始判定结果:\n{error['full_result']}\n")
            f.write("=" * 100 + "\n")

    print(f"\n详细错误记录已保存到: {output_file}")

else:
    print("\n未发现判定错误！")

# 检查是否有"否"被误判为"是"的情况
print("\n" + "=" * 100)
print("【补充检查】查看部分'否'记录确保规则正确")
print("=" * 100)

no_records_sample = df[df['结论'] == '否'].head(5)
for idx, row in no_records_sample.iterrows():
    content = str(row['反馈内容'])[:300]
    print(f"\n行号{row['行号']} - 结论: 否")
    print(f"内容: {content}...")
    print("-" * 50)

print("\n" + "=" * 100)
print("分析完成！")
print("=" * 100)
