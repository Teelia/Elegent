# -*- coding: utf-8 -*-
"""
在校学生信息完整性检查v1 - 错误统计分析脚本
"""
import pandas as pd
import re
from collections import Counter

def extract_student_info(text):
    """从文本中提取学生信息"""
    if pd.isna(text):
        return {}

    info = {
        'has_name': False,
        'has_id_card': False,
        'has_school': False,
        'has_grade': False,
        'has_department_major': False,
        'has_contact': False,
        'has_dropped_out': False,  # 是否辍学
        'has_graduated': False,  # 是否毕业
        'school_name': '',
        'age': None,
        'is_student': False,  # 是否明确标注为学生
    }

    text_str = str(text)

    # 检查是否明确标注"学生"
    info['is_student'] = '学生' in text_str or '在校' in text_str

    # 检查是否辍学
    info['has_dropped_out'] = '辍学' in text_str

    # 检查是否毕业
    info['has_graduated'] = '毕业' in text_str

    # 提取年龄
    age_match = re.search(r'(\d+)岁', text_str)
    if age_match:
        info['age'] = int(age_match.group(1))

    # 检查学校名称关键词
    school_patterns = [
        r'(学校|学院|中学|小学)[：:、，,\s]*([^，,。\s]+(?:学校|学院|中学|小学))',
        r'([^，,。\s]{2,10}(?:学校|学院|中学|小学|职教中心))',
    ]
    for pattern in school_patterns:
        matches = re.findall(pattern, text_str)
        if matches:
            info['has_school'] = True
            if isinstance(matches[0], tuple):
                info['school_name'] = matches[0][-1]
            else:
                info['school_name'] = matches[0]
            break

    # 检查身份证号
    info['has_id_card'] = bool(re.search(r'身份证号码?[:：]?\s*[1-9]\d{16}[\dXx]', text_str))

    # 检查年级/班级
    grade_patterns = [r'\d+年级', r'\d+届', r'\d+班', r'初[一二三]', r'高[一二三]',
                      r'大一|大二|大三|大四', r'研一|研二|研三']
    info['has_grade'] = any(re.search(pattern, text_str) for pattern in grade_patterns)

    # 检查院系/专业
    major_patterns = [r'专业[：:]?[^，,。\s]{2,20}', r'院[：:]?[^，,。\s]{2,20}',
                     r'系[：:]?[^，,。\s]{2,20}']
    info['has_department_major'] = any(re.search(pattern, text_str) for pattern in major_patterns)

    # 检查联系方式
    contact_patterns = [r'手机[号码号]?[:：]?\s*1[3-9]\d{9}',
                       r'电话[号码号]?[:：]?\s*1[3-9]\d{9}',
                       r'联系方式[:：]?\s*1[3-9]\d{9}']
    info['has_contact'] = any(re.search(pattern, text_str) for pattern in contact_patterns)

    # 检查姓名（简单判断：姓名在身份证号前面）
    name_matches = re.findall(r'([^\x00-\xff]{2,4})(?=身份证|护照|居民身份证)', text_str)
    info['has_name'] = len(name_matches) > 0

    return info


def classify_error_type(row_data):
    """分类错误类型"""
    conclusion = row_data.get('conclusion', '')
    reasoning = row_data.get('reasoning', '')
    student_info = row_data.get('student_info', {})

    # 如果结论是"否"，则不是我们要分析的"是"错误
    if conclusion != '是':
        return None

    error_types = []

    # 检查类型1：辍学学生被错误判定
    if student_info.get('has_dropped_out', False):
        error_types.append('辍学学生判定错误')

    # 检查类型2：学校名称可能是简称
    school_name = student_info.get('school_name', '')
    if school_name:
        # 检查是否为常见简称
        abbreviations = ['北师大', '安农', '中科大', '合工大', '安大']
        if any(abbr in school_name for abbr in abbreviations):
            error_types.append('学校名称可能是简称')

        # 检查是否缺少省级前缀
        if not any(prefix in school_name for prefix in ['安徽', '省', '市']):
            error_types.append('学校名称可能不完整（缺少省/市）')

    # 检查类型3：院系/专业信息可能缺失
    if student_info.get('is_student', False) and not student_info.get('has_dropped_out', False):
        # 对于高校/高职，应该有院系/专业
        if '学院' in school_name or '大学' in school_name:
            if not student_info.get('has_department_major', False):
                # 检查推理中是否声称有专业/院系
                if '专业' not in reasoning and '院系' not in reasoning and '系' not in reasoning:
                    error_types.append('可能缺少院系/专业信息')

    # 检查类型4：未识别学生身份
    if not student_info.get('is_student', False) and not student_info.get('has_dropped_out', False):
        age = student_info.get('age', None)
        if age and 6 <= age <= 18:
            error_types.append('未通过年龄识别学生身份')

    # 如果没有检测到任何错误类型，归类为"其他"
    if not error_types:
        error_types.append('其他错误类型')

    return error_types


def main():
    # 读取Excel文件
    file_path = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-75-results-with-reasoning.xlsx'
    df = pd.read_excel(file_path)

    last_col = '在校学生信息完整性检查_v1'

    # 解析每一行
    results = []

    for idx, row in df.iterrows():
        result_text = row[last_col]

        # 提取结论
        conclusion_match = re.search(r'结论[：:]\s*[是否]', str(result_text))
        conclusion = conclusion_match.group().replace('结论', '').replace('：', '').replace(':', '').strip() if conclusion_match else None

        # 提取推理
        reasoning_match = re.search(r'推理[：:]\s*(.+)', str(result_text), re.DOTALL)
        reasoning = reasoning_match.group(1).strip() if reasoning_match else str(result_text)

        # 提取学生信息（主要从列14提取）
        student_info = extract_student_info(row.get('列14', ''))

        result_data = {
            'row_idx': idx,
            'conclusion': conclusion,
            'reasoning': reasoning[:500],  # 截取前500字符
            'student_info': student_info,
            'original_data': row.iloc[1:-1].to_dict()
        }

        results.append(result_data)

    # 统计结论为"是"的错误类型
    yes_conclusions = [r for r in results if r['conclusion'] == '是']

    print("=" * 80)
    print("在校学生信息完整性检查v1 - 错误类型统计")
    print("=" * 80)
    print(f"\n结论为'是'的总数: {len(yes_conclusions)}\n")

    # 分类错误类型
    error_type_counts = Counter()
    error_details = []

    for result in yes_conclusions:
        error_types = classify_error_type(result)
        for error_type in error_types:
            error_type_counts[error_type] += 1

        error_details.append({
            'row': result['row_idx'] + 1,
            'error_types': error_types,
            'reasoning': result['reasoning'][:200],
            'dropped_out': result['student_info'].get('has_dropped_out', False),
            'age': result['student_info'].get('age', None),
            'school': result['student_info'].get('school_name', ''),
        })

    # 打印错误类型统计
    print("=" * 80)
    print("错误类型统计")
    print("=" * 80)
    for error_type, count in error_type_counts.most_common():
        percentage = count / len(yes_conclusions) * 100
        bar = "█" * int(percentage / 2)
        print(f"{error_type:40s} | {count:2d} ({percentage:5.1f}%) {bar}")

    # 保存详细错误列表
    output_file = r'D:\01_代码库\大模型自动化数据标签\task75_error_details.txt'
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write("=" * 80 + "\n")
        f.write("错误详情列表\n")
        f.write("=" * 80 + "\n\n")

        for detail in error_details:
            f.write(f"【第{detail['row']}行】\n")
            f.write(f"错误类型: {', '.join(detail['error_types'])}\n")
            f.write(f"推理摘要: {detail['reasoning']}\n")
            f.write(f"辍学: {detail['dropped_out']}\n")
            if detail['age']:
                f.write(f"年龄: {detail['age']}\n")
            if detail['school']:
                f.write(f"学校: {detail['school']}\n")
            f.write("-" * 80 + "\n\n")

    print(f"\n详细错误列表已保存到: {output_file}")

    # 输出辍学学生详情
    print("\n" + "=" * 80)
    print("辍学学生案例详情")
    print("=" * 80)
    dropped_out_cases = [d for d in error_details if d['dropped_out']]
    print(f"\n共发现 {len(dropped_out_cases)} 个辍学学生案例:\n")
    for case in dropped_out_cases[:10]:
        print(f"  第{case['row']}行: {case['reasoning'][:100]}...")
    if len(dropped_out_cases) > 10:
        print(f"  ... 还有 {len(dropped_out_cases) - 10} 个案例")

    # 输出年龄相关的案例
    print("\n" + "=" * 80)
    print("年龄推断案例（6-18岁）")
    print("=" * 80)
    age_cases = [d for d in error_details if d['age'] and 6 <= d['age'] <= 18]
    print(f"\n共发现 {len(age_cases)} 个6-18岁案例:\n")
    for case in age_cases[:10]:
        print(f"  第{case['row']}行: 年龄{case['age']}岁 - {case['reasoning'][:80]}...")
    if len(age_cases) > 10:
        print(f"  ... 还有 {len(age_cases) - 10} 个案例")


if __name__ == '__main__':
    main()
