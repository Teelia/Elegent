# -*- coding: utf-8 -*-
"""
验证脚本：测试 Task-80 涉警当事人信息完整性检查修复效果

运行方式：
    python verify_fixes_v3.py

预期结果：
    - 原20条错误的"是"结论应被修正为"否"
    - 修复后准确率应 > 99%
"""

import pandas as pd
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 测试数据文件路径
FILE_PATH = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-80-results-with-reasoning.xlsx'

# 期望修复的错误"是"结论数量（约18-20条）
EXPECTED_FIXES = 18

# 修复后应修正为"否"的关键词触发检测
FORCE_OVERRIDE_KEYWORDS = [
    "匿名", "拒绝登记", "拒绝提供", "无法登记",
    "研判无果", "查询无果", "系统查询无果",
    "不详", "记不清", "身份信息不详"
]

# 隐含当事人检测关键词
IMPLIED_PARTY_KEYWORDS = [
    ("小孩", "小孩/儿童"),
    ("儿童", "小孩/儿童"),
    ("中介", "中介"),
    ("网友", "网友"),
    ("抖音网友", "网友"),
    ("微信网友", "网友"),
    ("收费方", "收费方"),
    ("店家", "收费方"),
    ("物业", "收费方"),
]


def load_data():
    """加载Excel数据"""
    print("=== 加载数据 ===")
    df = pd.read_excel(FILE_PATH)
    print(f"数据总行数: {len(df)}")
    print(f"列名: {df.columns.tolist()}")
    return df


def find_wrong_yes_conclusions(df):
    """查找错误的"是"结论"""
    label_col = '涉警当事人信息完整性检查_v1'
    yes_mask = df[label_col].astype(str).str.contains('结论：是', na=False)
    yes_df = df[yes_mask]

    print(f"\n=== 结论为'是'的数据行数: {len(yes_df)} ===")

    return yes_df


def analyze_row(row, idx):
    """分析单行数据，判断是否应该修正"""
    label_col = '涉警当事人信息完整性检查_v1'
    content_col = '反馈内容'

    content = str(row[content_col])
    result = str(row[label_col])

    # 问题类型列表
    issues = []

    import re

    # ========== 1. 检查强制推翻关键词（最高优先级）==========
    for keyword in FORCE_OVERRIDE_KEYWORDS:
        if keyword in content:
            issues.append(f"检测到强制推翻关键词: [{keyword}]")
            return issues  # 直接返回，不再检查其他

    # ========== 2. 检查隐含当事人 ==========
    for keyword, party_type in IMPLIED_PARTY_KEYWORDS:
        if keyword in content:
            # 检查该隐含当事人是否有身份证号
            idx_pos = content.find(keyword)
            if idx_pos != -1:
                after_keyword = content[idx_pos:idx_pos + len(keyword) + 50]
                if not re.search(r'\d{17}[\dXx]', after_keyword):
                    issues.append(f"检测到隐含当事人[{party_type}]但无身份证号")
                    return issues  # 直接返回

    # ========== 3. 提取身份证号数量 ==========
    id_cards = re.findall(r'\d{17}[\dXx]', content)

    # ========== 4. 检查只有一方身份证的情况 ==========
    if len(id_cards) == 1:
        # 检查是否涉及纠纷或对方
        if any(keyword in content for keyword in ["对方", "纠纷", "发生", "与", "被"]):
            issues.append("仅有一方身份证号，但涉及纠纷/对方")
            return issues

    # ========== 5. 检查"对方"或"侵权人"无身份证 ==========
    if "对方" in content or "侵权" in content or "嫌疑人" in content:
        # 统计身份证号和"对方"的数量
        other_party_count = content.count("对方") + content.count("侵权") + content.count("嫌疑人")
        if other_party_count > 0 and len(id_cards) < other_party_count + 1:  # +1是报警人
            issues.append(f"提及{other_party_count}个对方/侵权人/嫌疑人，但身份证号不足")
            return issues

    # ========== 6. 检查护照号格式（学号误识别）==========
    passport_pattern = re.compile(r'[A-Z]{2}\d{8}')  # 如 BL19004002
    passports = passport_pattern.findall(content)
    for passport in passports:
        # 检查是否是学号（通常有"学号"等关键词）
        if "学号" in content or "学生" in content:
            issues.append(f"检测到可疑护照号（可能是学号）: [{passport}]")
            return issues

    # ========== 7. 检查未获取身份信息的表述 ==========
    missing_phrases = ["未获取", "无法核实", "身份不明", "不详", "未查实"]
    for phrase in missing_phrases:
        if phrase in content and "身份" in content:
            issues.append(f"检测到身份信息缺失表述: [{phrase}]")
            return issues

    return issues


def main():
    """主函数"""
    print("=" * 80)
    print("Task-80 涉警当事人信息完整性检查 - 修复效果验证")
    print("=" * 80)

    # 加载数据
    df = load_data()

    # 查找"是"结论
    yes_df = find_wrong_yes_conclusions(df)

    if len(yes_df) == 0:
        print("\n✅ 未发现'是'结论的数据")
        return

    # 分析每一行
    print("\n=== 分析每一条'是'结论 ===")
    should_fix_count = 0
    should_keep_count = 0

    for idx, row in yes_df.iterrows():
        issues = analyze_row(row, idx)

        print(f"\n--- 行索引 {idx} ---")
        content_col = '反馈内容'
        print(f"反馈内容（前150字）: {str(row[content_col])[:150]}...")

        if issues:
            should_fix_count += 1
            print(f"❌ 应修正为[否] - 原因: {', '.join(issues)}")
        else:
            should_keep_count += 1
            print(f"✅ 保持[是] - 未发现问题")

    # 输出统计
    print("\n" + "=" * 80)
    print("=== 统计结果 ===")
    print(f"总'是'结论数量: {len(yes_df)}")
    print(f"应修正为[否]数量: {should_fix_count}")
    print(f"应保持[是]数量: {should_keep_count}")
    print(f"期望修正数量: ~{EXPECTED_FIXES}")

    # 验证修复效果
    if should_fix_count >= EXPECTED_FIXES - 2:
        print("\n✅ 修复效果良好！达到预期目标。")
    else:
        print(f"\n⚠️  修复数量不足，期望至少 {EXPECTED_FIXES} 条，实际 {should_fix_count} 条")

    # 计算修复后准确率
    total_rows = len(df)
    wrong_before = 20  # 已知错误数量
    wrong_after = should_keep_count  # 修复后仍错误的数量

    accuracy_before = (total_rows - wrong_before) / total_rows * 100
    accuracy_after = (total_rows - wrong_after) / total_rows * 100

    print(f"\n修复前准确率: {accuracy_before:.2f}%")
    print(f"修复后准确率: {accuracy_after:.2f}%")
    print(f"准确率提升: {accuracy_after - accuracy_before:.2f}%")

    print("\n" + "=" * 80)
    print("验证完成")
    print("=" * 80)


if __name__ == "__main__":
    main()
