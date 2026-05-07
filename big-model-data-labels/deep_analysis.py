# -*- coding: utf-8 -*-
"""
深度分析：找出28条"是"判定的真正错误原因
"""
import pandas as pd
import re
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

file_path = r"D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-78-results-with-reasoning (1).xlsx"
df = pd.read_excel(file_path)

def extract_conclusion(text):
    if pd.isna(text):
        return None
    match = re.search(r'结论[：:]\s*([是否])', str(text))
    return match.group(1) if match else None

df['结论'] = df['涉警当事人信息完整性检查_v1'].apply(extract_conclusion)
yes_records = df[df['结论'] == '是'].copy()

print("=" * 100)
print("深度分析：28条'是'判定的错误原因")
print("=" * 100)

# 改进身份证号检测函数
def extract_id_cards_detailed(text):
    """
    详细提取身份证号，包括其上下文
    """
    # 匹配各种格式的身份证号
    patterns = [
        # 标准格式：18位数字，可能以X结尾
        r'\b(\d{17}[\dXx])\b',
        # 15位老格式
        r'\b(\d{15})\b',
        # 可能被问号或其他字符打断的
        r'[?？]*(\d{17}[\dXx]|\d{15})',
    ]

    results = []
    for pattern in patterns:
        matches = re.finditer(pattern, text)
        for match in matches:
            id_num = match.group(1)
            # 获取上下文（前后20个字符）
            start = max(0, match.start() - 20)
            end = min(len(text), match.end() + 20)
            context = text[start:end]

            results.append({
                'id': id_num,
                'length': len(id_num),
                'context': context,
                'has_prefix': bool(re.search(r'[?？]', text[max(0, match.start()-2):match.start()])),
                'position': match.start()
            })

    return results

# 分析每一条记录
all_errors = []

for idx, row in yes_records.iterrows():
    row_num = row['行号']
    content = str(row['反馈内容'])
    full_result = str(row['涉警当事人信息完整性检查_v1'])

    print(f"\n{'='*100}")
    print(f"行号: {row_num}")
    print(f"{'='*100}")

    # 提取身份证号
    id_cards = extract_id_cards_detailed(content)

    print(f"\n【身份证号详细分析】:")
    print(f"共提取到 {len(id_cards)} 个身份证号")

    valid_count = 0
    invalid_list = []

    for i, card_info in enumerate(id_cards, 1):
        print(f"\n  #{i}: {card_info['id']} ({card_info['length']}位)")
        print(f"      上下文: ...{card_info['context']}...")

        # 检查有效性
        issues = []

        # 1. 检查长度
        if card_info['length'] == 18:
            issues.append("✓ 长度正确(18位)")
        elif card_info['length'] == 15:
            issues.append("⚠ 老格式(15位)")
        else:
            issues.append(f"✗ 长度异常({card_info['length']}位)")

        # 2. 检查前缀字符
        if card_info['has_prefix']:
            issues.append("✗ 前缀有特殊字符(如问号)")

        # 3. 检查上下文中的标记
        context_lower = card_info['context'].lower()
        if any(kw in card_info['context'] for kw in ['无果', '未提供', '记不清', '不详']):
            issues.append("✗ 身份信息缺失标记")

        # 4. 18位身份证校验码验证（简化版）
        if card_info['length'] == 18:
            id_num = card_info['id']
            # 最后一位应该是数字或X
            if id_num[-1] not in '0123456789Xx':
                issues.append("✗ 最后一位格式错误")
            else:
                valid_count += 1

        for issue in issues:
            print(f"      {issue}")

        if '✗' in str(issues):
            invalid_list.append(card_info['id'])

    # 提取判断依据
    reasoning_match = re.search(r'判断依据[：:](.*?)(?:\[|$)', full_result, re.DOTALL)
    reasoning = reasoning_match.group(1).strip() if reasoning_match else "无"
    print(f"\n【系统判断依据】:")
    print(f"  {reasoning[:200]}...")

    # 分析问题
    print(f"\n【问题诊断】:")

    # 检查：如果有任何身份证号被标记为无效
    has_invalid = len(invalid_list) > 0
    # 检查：实际有效身份证数 vs 提取到的总数
    valid_ratio = valid_count / len(id_cards) if id_cards else 0

    print(f"  - 有效身份证: {valid_count}/{len(id_cards)} ({valid_ratio*100:.1f}%)")
    print(f"  - 有问题的身份证: {len(invalid_list)}个")

    # 检查特殊问题
    special_issues = []

    # 问题1：身份证号前有问号等前缀
    if any(card['has_prefix'] for card in id_cards):
        special_issues.append("身份证号前缀有特殊字符导致格式错误")

    # 问题2：护照号被误判
    passport_pattern = r'[A-Z][A-Z0-9]{6,15}'
    passports = re.findall(passport_pattern, content)
    if passports:
        print(f"  - 发现护照号: {passports}")
        # 检查是否被系统错误判定为合格
        if '护照' in reasoning and len(id_cards) == 0:
            special_issues.append("仅有护照号但系统判定合格")

    # 问题3：未匹配的涉警当事人
    # 尝试提取人名
    name_patterns = [
        r'报警人[：:]\s*([^\s,，（(]{2,4})',
        r'([\u4e00-\u9fa5]{2,3})[（(]\s*(?:男|女)',
        r'与([\u4e00-\u9fa5]{2,3})[，,）\s]',
    ]
    names = set()
    for pattern in name_patterns:
        matches = re.findall(pattern, content)
        names.update(matches)

    if names:
        print(f"  - 提取到人名: {list(names)[:10]}")
        if len(names) > valid_count:
            special_issues.append(f"涉警当事人数量({len(names)})多于有效身份证数({valid_count})")

    # 问题4：撤警关键词误判
    cancel_keywords = ['撤回', '撤警', '无效']
    has_cancel = any(kw in content for kw in cancel_keywords)
    if has_cancel:
        # 检查是否真的是撤警
        if '反馈110指令，撤回' in content or '请求撤警' in content:
            print(f"  - 确认是撤警情形（合格）")
        else:
            special_issues.append("包含撤警相关词汇但非真实撤警情形")

    for issue in special_issues:
        print(f"  ⚠️ {issue}")

    # 结论
    is_error = has_invalid or len(special_issues) > 0
    if is_error:
        print(f"\n  【结论】：⚠️ 判定错误，应该是'否'")
        all_errors.append({
            'row': row_num,
            'issues': special_issues + ([f"无效身份证: {invalid_list}"] if invalid_list else [])
        })
    else:
        print(f"\n  【结论】：✓ 判定正确")

# 总结
print("\n" + "=" * 100)
print("【错误原因汇总】")
print("=" * 100)

if all_errors:
    issue_types = {}
    for error in all_errors:
        for issue in error['issues']:
            issue_types[issue] = issue_types.get(issue, 0) + 1

    print("\n发现的主要错误类型:")
    for issue, count in sorted(issue_types.items(), key=lambda x: -x[1]):
        print(f"  • {issue}: {count}条")
