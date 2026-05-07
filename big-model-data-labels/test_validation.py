# -*- coding: utf-8 -*-
"""
优化效果验证脚本
用于对比v1和v2标签的分析结果
"""

import pandas as pd
import json
import sys
import io
from pathlib import Path
from typing import Dict, List, Tuple
import re

# 设置输出编码
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


class ResultValidator:
    """结果验证器"""

    def __init__(self, original_file: str):
        """
        初始化验证器

        Args:
            original_file: 原始结果文件路径
        """
        self.original_df = pd.read_excel(original_file)
        self.label_col = '涉警当事人信息完整性检查_v1'

    def validate_row(self, row_num: int, row_data: pd.Series) -> Dict:
        """
        验证单行数据

        Args:
            row_num: 行号
            row_data: 行数据

        Returns:
            验证结果字典
        """
        result = {
            'row_num': row_num,
            'content': str(row_data['反馈内容'])[:200],
            'llm_result': row_data[self.label_col],
            'issues': [],
            'should_be': None
        }

        # 提取身份证号
        content = str(row_data['反馈内容'])
        id_numbers = re.findall(r'\d{15,19}[\dXx]?', content)

        # 检查1: 身份证号格式
        for id_num in id_numbers:
            if len(id_num) != 18:
                result['issues'].append(f'身份证号长度错误: {id_num} ({len(id_num)}位)')
            elif not re.match(r'^\d{17}[\dXx]$', id_num):
                result['issues'].append(f'身份证号格式错误: {id_num}')

        # 检查2: 信息缺失关键词
        missing_keywords = [
            '拒绝', '拒绝登记', '拒绝提供', '拒不透露',
            '未提供', '未登记', '无法', '无法登记',
            '研判无果', '无果', '不详', '记不清'
        ]

        for keyword in missing_keywords:
            if keyword in content:
                result['issues'].append(f'检测到信息缺失关键词: {keyword}')
                break

        # 检查3: 隐含当事人
        implicit_patterns = [
            (r'小孩.*被(咬伤|打|推搡)', '小孩受害者'),
            (r'中介.*电话', '中介'),
            (r'收费方|停车费.*纠纷', '收费方'),
        ]

        for pattern, desc in implicit_patterns:
            if re.search(pattern, content):
                # 检查是否有身份信息
                if '身份证' not in content[:content.find(desc)+100]:
                    result['issues'].append(f'存在隐含当事人但无身份信息: {desc}')

        # 判断应该是"是"还是"否"
        if result['issues']:
            result['should_be'] = '否'
        else:
            result['should_be'] = '是'

        # 检查LLM结果是否正确
        if '结论：是' in result['llm_result'] and result['should_be'] == '否':
            result['is_correct'] = False
        elif '结论：否' in result['llm_result'] and result['should_be'] == '是':
            result['is_correct'] = False
        else:
            result['is_correct'] = True

        return result

    def validate_all(self) -> Tuple[List[Dict], Dict]:
        """
        验证所有行

        Returns:
            (错误列表, 统计信息)
        """
        errors = []
        stats = {
            'total': len(self.original_df),
            'correct': 0,
            'incorrect': 0,
            'issue_types': {}
        }

        for idx, row in self.original_df.iterrows():
            validation = self.validate_row(row['行号'], row)

            if not validation['is_correct']:
                errors.append(validation)
                stats['incorrect'] += 1

                # 统计错误类型
                for issue in validation['issues']:
                    issue_type = issue.split(':')[0]
                    stats['issue_types'][issue_type] = stats['issue_types'].get(issue_type, 0) + 1
            else:
                stats['correct'] += 1

        return errors, stats

    def compare_with_new_result(self, new_file: str) -> Dict:
        """
        对比新旧结果

        Args:
            new_file: 新结果文件路径

        Returns:
            对比结果
        """
        new_df = pd.read_excel(new_file)

        comparison = {
            'fixed': 0,  # 修复的错误
            'new_errors': 0,  # 新增的错误
            'still_errors': 0,  # 仍未修复的错误
            'fixed_list': [],
            'new_error_list': []
        }

        for idx in range(len(self.original_df)):
            original_row = self.original_df.iloc[idx]
            new_row = new_df.iloc[idx]

            original_result = original_row[self.label_col]
            new_result = new_row[self.label_col]

            # 验证是否真的正确
            validation = self.validate_row(original_row['行号'], original_row)
            correct_answer = validation['should_be']

            original_is_correct = (
                ('结论：是' in original_result and correct_answer == '是') or
                ('结论：否' in original_result and correct_answer == '否')
            )

            new_is_correct = (
                ('结论：是' in new_result and correct_answer == '是') or
                ('结论：否' in new_result and correct_answer == '否')
            )

            if not original_is_correct and new_is_correct:
                comparison['fixed'] += 1
                comparison['fixed_list'].append({
                    'row_num': original_row['行号'],
                    'issue': validation['issues'][0] if validation['issues'] else '未知'
                })
            elif original_is_correct and not new_is_correct:
                comparison['new_errors'] += 1
                comparison['new_error_list'].append({
                    'row_num': original_row['行号'],
                    'issue': validation['issues'][0] if validation['issues'] else '未知'
                })
            elif not original_is_correct and not new_is_correct:
                comparison['still_errors'] += 1

        return comparison


def print_report(errors: List[Dict], stats: Dict):
    """打印验证报告"""
    print("=" * 100)
    print("优化效果验证报告")
    print("=" * 100)
    print(f"\n总行数: {stats['total']}")
    print(f"正确: {stats['correct']} ({stats['correct']/stats['total']*100:.1f}%)")
    print(f"错误: {stats['incorrect']} ({stats['incorrect']/stats['total']*100:.1f}%)")

    print(f"\n错误类型分布:")
    for issue_type, count in sorted(stats['issue_types'].items(), key=lambda x: -x[1]):
        print(f"  - {issue_type}: {count}")

    print(f"\n前20个错误案例:")
    for i, error in enumerate(errors[:20], 1):
        print(f"\n{i}. 行号 {error['row_num']}: {error['content']}")
        print(f"   问题: {', '.join(error['issues'])}")
        print(f"   LLM结果: {error['llm_result'][:50]}...")
        print(f"   应该判定为: {error['should_be']}")


def print_comparison(comparison: Dict):
    """打印对比报告"""
    print("\n" + "=" * 100)
    print("v1 vs v2 对比结果")
    print("=" * 100)
    print(f"\n修复的错误: {comparison['fixed']}")
    print(f"新增的错误: {comparison['new_errors']}")
    print(f"仍未修复: {comparison['still_errors']}")
    print(f"净改进: {comparison['fixed'] - comparison['new_errors']}")

    if comparison['fixed_list']:
        print(f"\n修复案例（前10个）:")
        for item in comparison['fixed_list'][:10]:
            print(f"  - 行号 {item['row_num']}: {item['issue']}")

    if comparison['new_error_list']:
        print(f"\n新增错误案例（前10个）:")
        for item in comparison['new_error_list'][:10]:
            print(f"  - 行号 {item['row_num']}: {item['issue']}")


def main():
    """主函数"""
    original_file = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-74-results-with-reasoning.xlsx'

    print("加载原始结果...")
    validator = ResultValidator(original_file)

    print("验证所有行...")
    errors, stats = validator.validate_all()
    print_report(errors, stats)

    # 如果有新结果文件，进行对比
    # new_file = r'D:\01_代码库\大模型自动化数据标签\测试数据\analysis-task-74-results-v2.xlsx'
    # if Path(new_file).exists():
    #     print("\n加载新结果...")
    #     comparison = validator.compare_with_new_result(new_file)
    #     print_comparison(comparison)


if __name__ == '__main__':
    main()
