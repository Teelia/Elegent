# -*- coding: utf-8 -*-
"""
全面分析28条错误记录 - 根据标签定义的严格标准
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

print("=" * 120)
print("28条'是'判定记录的全面错误分析")
print("=" * 120)

# 定义标签规则
print("\n【标签定义回顾】:")
print("  合格标准：所有涉警当事人均应提供有效身份号码（18位身份证号或护照号）")
print("  不合格标准：存在任一涉警当事人身份证号缺失/不完整/格式错误，且无法以护照号补齐")
print("  撤警情形：包含撤警/无效警情/无事实等关键词，直接判定为'是'")

def analyze_record(content, full_result):
    """
    根据标签定义全面分析一条记录
    返回：(是否应该为否, 问题列表)
    """
    issues = []

    # 1. 检查撤警关键词
    cancel_keywords = ['撤警', '无效警情', '无事实', '误报', '重复警情', '未发现报警情况', '请求撤警']
    has_cancel = any(kw in content for kw in cancel_keywords)

    # 需要确认是否真的是撤警（而不仅仅是包含"撤回"等词）
    # 只有真正的撤警情况才能直接判定为"是"
    is_real_cancel = (
        '请求撤警' in content or
        '无效警情' in content or
        '撤警' in content and '反馈110指令，撤回' in content
    )

    if is_real_cancel:
        return False, ["真正的撤警情形，判定为'是'是正确的"]

    if has_cancel and not is_real_cancel:
        issues.append("包含撤警关键词但非真实撤警情形")

    # 2. 提取所有身份证号及其上下文
    # 改进的身份证号提取：捕获前缀和后缀
    id_with_context = []
    id_pattern = r'([?？#＊]*\d{15}|\d{17}[\dXx]|\d{18})'
    for match in re.finditer(id_pattern, content):
        id_num = match.group(1)
        context_start = max(0, match.start() - 15)
        context_end = min(len(content), match.end() + 15)
        context = content[context_start:context_end]

        id_with_context.append({
            'original': id_num,
            'clean': re.sub(r'[^0-9Xx]', '', id_num),
            'context': context,
            'has_prefix': bool(re.search(r'[?？#＊]', id_num))
        })

    # 3. 验证每个身份证号
    valid_ids = []
    invalid_ids = []

    for id_info in id_with_context:
        original = id_info['original']
        clean = id_info['clean']

        # 检查前缀
        if id_info['has_prefix']:
            invalid_ids.append(f"{original}(前缀有特殊字符)")
            continue

        # 检查长度
        if len(clean) == 18:
            # 检查格式
            if clean[-1] not in '0123456789Xx':
                invalid_ids.append(f"{clean}(最后一位错误)")
            else:
                valid_ids.append(clean)
        elif len(clean) == 15:
            invalid_ids.append(f"{clean}(15位老格式)")
        else:
            invalid_ids.append(f"{original}(长度{len(clean)}位)")

    # 4. 检查护照号
    passport_pattern = r'\b([A-Z]{1,2}\d{7,9})\b'
    passports = re.findall(passport_pattern, content)
    valid_passports = []
    for p in passports:
        # 中国护照通常是G或P开头，8位数字
        if re.match(r'^[GP]\d{8}$', p):
            valid_passports.append(p)
        elif re.match(r'^[A-Z]{2}\d{7}$', p):
            valid_passports.append(p)
        else:
            issues.append(f"护照号{p}格式不符合标准")

    # 5. 提取涉警当事人
    parties = []

    # 模式1: 姓名（身份证）
    party_pattern1 = r'([\u4e00-\u9fa5]{2,3})[（(]\s*(?:男|女|身份证|身份)'
    matches = re.findall(party_pattern1, content)
    parties.extend(matches)

    # 模式2: 报警人：姓名
    party_pattern2 = r'报警人[：:]\s*([^\s,，（(]{2,4})'
    matches = re.findall(party_pattern2, content)
    parties.extend(matches)

    # 模式3: 与XXX发生纠纷
    party_pattern3 = r'与([\u4e00-\u9fa5]{2,3})[，,）\s](?:发生|因)'
    matches = re.findall(party_pattern3, content)
    parties.extend(matches)

    # 去重
    parties = list(set(parties))
    # 过滤掉明显不是人名的词
    exclude_words = {'报警', '对方', '双方', '当事人', '纠纷', '发生', '系', '称', '经查', '经了解', '我单位'}
    parties = [p for p in parties if p not in exclude_words and len(p) >= 2]

    # 6. 检查身份缺失标记
    missing_markers = [
        '未提供身份证', '无身份证', '身份证缺失', '记不清身份证', '身份证号记不住',
        '拒绝登记身份', '拒绝登记信息', '未提供身份', '无身份信息', '身份证号不详',
        '研判无果', '再次研判无果', '未掌握', '无法核实', '系统查询无果',
        '当事人拒绝', '不配合登记', '无法确认'
    ]

    found_missing_markers = [marker for marker in missing_markers if marker in content]
    if found_missing_markers:
        issues.append(f"发现身份缺失标记: {', '.join(found_missing_markers[:3])}")

    # 7. 检查是否有当事人没有身份证
    if len(parties) > len(valid_ids) + len(valid_passports):
        issues.append(f"涉警当事人数量({len(parties)})多于有效身份信息数量({len(valid_ids) + len(valid_passports)})")

    # 8. 检查无效身份证号
    if invalid_ids:
        issues.append(f"发现无效身份证号: {', '.join(invalid_ids[:3])}")

    # 9. 特殊情况：只有一个身份证号，但提到多个当事人
    if len(valid_ids) == 1 and len(parties) > 1 and len(valid_passports) == 0:
        issues.append(f"仅1个有效身份证号，但有{len(parties)}个涉警当事人")

    # 10. 特殊情况：公司作为当事人
    if '公司' in content and len(valid_ids) == 0 and len(valid_passports) == 0:
        issues.append("涉及公司但无有效身份信息")

    # 判断是否应该为"否"
    should_be_no = (
        not is_real_cancel and  # 不是真实撤警
        len(issues) > 0  # 有任何问题
    )

    return should_be_no, issues

# 分析所有"是"记录
errors = []

for idx, row in yes_records.iterrows():
    row_num = row['行号']
    content = str(row['反馈内容'])
    full_result = str(row['涉警当事人信息完整性检查_v1'])

    print(f"\n{'='*120}")
    print(f"行号: {row_num}")
    print(f"{'='*120}")

    # 显示内容摘要
    print(f"\n【内容摘要】:")
    print(f"  {content[:150]}...")

    should_be_no, issues = analyze_record(content, full_result)

    print(f"\n【问题列表】:")
    if issues:
        for issue in issues:
            print(f"  ⚠️ {issue}")
        print(f"\n  【结论】: 应该判定为 '否'")
        errors.append({'row': row_num, 'issues': issues})
    else:
        print(f"  ✓ 未发现问题")
        print(f"\n  【结论】: 判定为 '是' 是正确的")

# 总结
print("\n" + "=" * 120)
print("【最终统计】")
print("=" * 120)

print(f"\n总'是'记录数: {len(yes_records)}")
print(f"发现错误记录数: {len(errors)}")
print(f"用户报告错误数: 28")
print(f"误差: {abs(len(errors) - 28)}条")

if errors:
    print("\n【错误原因分类统计】:")
    error_types = {}
    for error in errors:
        for issue in error['issues']:
            # 提取错误类型（简化描述）
            error_type = issue.split('(')[0].split(':')[0].strip()
            error_types[error_type] = error_types.get(error_type, 0) + 1

    for error_type, count in sorted(error_types.items(), key=lambda x: -x[1]):
        print(f"  • {error_type}: {count}条")
