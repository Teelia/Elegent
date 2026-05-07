#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
逐行审计：Task-90（在校学生）与 Task-91（涉警当事人）

输入：
  - （默认）测试数据/analysis-task-90-results-with-reasoning (1).xlsx
  - （默认）测试数据/analysis-task-91-results-with-reasoning.xlsx
  - （可选）通过命令行参数 --task90/--task91 指定其他导出文件

输出：
  - 测试数据/audit_task90_task91_<timestamp>.xlsx

说明：
  - 该脚本只做“可追溯的逐行归因”，不修改原始测试文件。
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Optional, Dict, Any, List, Tuple

import pandas as pd


TASK90_PATH = Path(r"测试数据/analysis-task-90-results-with-reasoning (1).xlsx")
TASK91_PATH = Path(r"测试数据/analysis-task-91-results-with-reasoning.xlsx")

SHEET_NAME = "分析结果"

COL_COMMON = ["行号", "事件单编号", "接警单位", "所属分局", "反馈内容"]
COL_TASK90 = "在校学生信息完整性检查_v1"
COL_TASK91 = "涉警当事人信息完整性检查_v1"

def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="逐行审计：Task90/Task91 结果表（含推理内容）")
    p.add_argument("--task90", type=str, default=str(TASK90_PATH), help="Task-90 导出xlsx路径")
    p.add_argument("--task91", type=str, default=str(TASK91_PATH), help="Task-91 导出xlsx路径")
    p.add_argument("--sheet", type=str, default=SHEET_NAME, help="工作表名称（默认：分析结果）")
    p.add_argument("--out", type=str, default="", help="输出xlsx路径（默认写入测试数据/audit_task90_task91_<ts>.xlsx）")
    return p.parse_args()


def _s(x: Any) -> str:
    return "" if x is None else str(x)


def parse_conclusion(text: str) -> Optional[str]:
    t = _s(text)
    m = re.search(r"结论[:：]\s*([是否])", t)
    if m:
        return m.group(1)
    # 兼容极简输出
    t2 = t.strip()
    if t2 in ("是", "否"):
        return t2
    return None


def parse_post_validation_status(text: str) -> Optional[str]:
    t = _s(text)
    if "后置规则验证通过" in t:
        return "通过"
    if "后置规则验证:" in t or "后置规则验证：" in t:
        return "失败"
    # 某些输出可能只出现“后置验证失败/后置规则验证”
    if "后置验证失败" in t:
        return "失败"
    return None


def extract_missing_keyword(text: str) -> Optional[str]:
    t = _s(text)
    m = re.search(r"检测到信息缺失关键词\[(.+?)\]", t)
    return m.group(1) if m else None


def has_anonymous(text: str) -> bool:
    t = _s(text)
    return ("检测到[匿名]" in t) or ("检测到[匿名" in t) or ("匿名报警" in t) or ("匿名报案" in t)


def extract_id_prefix_error(text: str) -> Optional[Dict[str, Any]]:
    """
    解析“身份证号前存在N个前缀字符...”这类输出。
    额外判断：前缀是否仅为空白（空格/Tab/换行），用于定位误杀。
    """
    t = _s(text)
    if "身份证号前存在" not in t:
        return None
    # 例：身份证号格式错误:  340... (原因: 身份证号前存在1个前缀字符（如?#等），导致格式错误： 340...)
    m = re.search(r"格式错误：\s*(.*?)(?:\)|;|\n)", t)
    raw = m.group(1) if m else None
    raw = raw if raw is None else raw[:64]
    prefix_type = None
    if raw is not None:
        # raw 常见形态是以空格开头：' 340...'
        lead = raw[:1]
        if lead.isspace():
            prefix_type = "空白"
        elif lead in ("?", "？", "#", "*", "＊"):
            prefix_type = "特殊字符"
        else:
            prefix_type = "未知"
    return {"raw": raw, "prefix_type": prefix_type}


def extract_party_missing(text: str) -> Optional[Dict[str, Any]]:
    t = _s(text)
    m = re.search(r"涉警当事人共(\d+)人，仅(\d+)人提供有效身份信息，缺失(\d+)人：([^\n]+)", t)
    if not m:
        return None
    total = int(m.group(1))
    miss = int(m.group(3))
    names = m.group(4).strip()
    return {"total": total, "missing": miss, "names": names}


def has_withdrawal_hit(text: str) -> bool:
    t = _s(text)
    return ("触发直接合格" in t) or ("未发现报警情况" in t) or ("命中" in t and "关键词" in t and "触发" in t)


def detect_cross_pollution_for_student(text: str) -> bool:
    """
    在校学生标签输出中出现涉警当事人/身份证格式错误/强制信息缺失等，
    通常意味着走了涉警的通用后置验证链路（labelName 未透传）。
    """
    t = _s(text)
    if "涉警当事人共" in t:
        return True
    if "身份证号格式错误" in t or "身份证号前存在" in t:
        return True
    # 该类关键词来自涉警后置验证（非学生专用验证口径）
    if "检测到信息缺失关键词" in t or "检测到[匿名]" in t:
        return True
    return False


def build_row_conclusion(label_type: str, parsed: Dict[str, Any]) -> str:
    """
    逐行“归因结论”短句：用于快速筛选与回归验证。
    """
    issues: List[str] = []

    if label_type == "在校学生" and parsed.get("cross_pollution"):
        issues.append("在校学生标签疑似被涉警后置规则污染（labelName 未透传）")

    idp = parsed.get("id_prefix_error")
    if idp and idp.get("prefix_type") == "空白":
        issues.append("身份证前缀误判：仅空白前缀导致格式错误（应修复）")
    elif idp:
        issues.append(f"身份证前缀错误：{idp.get('prefix_type') or '未知'}")

    mk = parsed.get("missing_keyword")
    if mk:
        issues.append(f"信息缺失关键词触发：[{mk}]")

    pm = parsed.get("party_missing")
    if pm:
        issues.append(f"当事人缺失身份：缺失{pm['missing']}人（名单可能含噪声）")

    if parsed.get("anonymous"):
        issues.append("匿名/匿名报案触发强制否")

    if parsed.get("withdrawal_hit"):
        issues.append("撤警/无效警情关键词触发直接合格（需确认口径是否允许绕过完整性）")

    if not issues:
        return "未发现明显规则触发项（建议结合原文与模型推理复核）"
    return "；".join(issues)


def expected_after_fix(label_type: str, parsed: Dict[str, Any]) -> str:
    """
    给出“修复后预期影响”的粗粒度判断（用于优先回归抽样）。
    """
    if label_type == "在校学生" and parsed.get("cross_pollution"):
        return "高：修复 labelName 透传后，该行后置验证与结论/理由可能变化"

    idp = parsed.get("id_prefix_error")
    if idp and idp.get("prefix_type") == "空白":
        return "中：修复身份证空白前缀误判后，该行可能不再因前缀被强制否"

    pm = parsed.get("party_missing")
    if pm:
        # 仅展示层降噪一般不改结论，但能减少误导；若未来调整口径可能影响结论
        return "低~中：当事人缺失名单展示会更干净；若缺失名单仅为噪声，可能影响后续判定"

    return "低：预计修复后变化不大"


def audit_one(df: pd.DataFrame, label_type: str, label_col: str) -> pd.DataFrame:
    out_rows: List[Dict[str, Any]] = []

    for _, r in df.iterrows():
        raw = _s(r.get(label_col))
        concl = parse_conclusion(raw)
        post = parse_post_validation_status(raw)
        mk = extract_missing_keyword(raw)
        idp = extract_id_prefix_error(raw)
        pm = extract_party_missing(raw)
        anon = has_anonymous(raw)
        wd = has_withdrawal_hit(raw)
        cross = detect_cross_pollution_for_student(raw) if label_type == "在校学生" else False

        parsed = {
            "conclusion": concl,
            "post_validation": post,
            "missing_keyword": mk,
            "id_prefix_error": idp,
            "party_missing": pm,
            "anonymous": anon,
            "withdrawal_hit": wd,
            "cross_pollution": cross,
        }

        issue_tags: List[str] = []
        if cross:
            issue_tags.append("labelName污染")
        if idp and idp.get("prefix_type") == "空白":
            issue_tags.append("身份证空白前缀误判")
        elif idp:
            issue_tags.append("身份证前缀错误")
        if mk:
            issue_tags.append("信息缺失关键词")
        if anon:
            issue_tags.append("匿名")
        if pm:
            issue_tags.append("当事人抽取/缺失")
        if wd:
            issue_tags.append("撤警/无效警情直通")

        out_rows.append({
            "标签类型": label_type,
            "行号": r.get("行号"),
            "事件单编号": r.get("事件单编号"),
            "接警单位": r.get("接警单位"),
            "所属分局": r.get("所属分局"),
            "反馈内容": r.get("反馈内容"),
            "原始标签输出": raw,
            "解析_结论": concl,
            "解析_后置验证状态": post,
            "解析_信息缺失关键词": mk,
            "解析_匿名": anon,
            "解析_撤警直通": wd,
            "解析_身份证前缀类型": None if not idp else idp.get("prefix_type"),
            "解析_身份证原始片段": None if not idp else idp.get("raw"),
            "解析_涉警当事人总数": None if not pm else pm.get("total"),
            "解析_涉警当事人缺失数": None if not pm else pm.get("missing"),
            "解析_涉警缺失名单片段": None if not pm else pm.get("names"),
            "诊断_疑似跨标签污染": cross,
            "诊断_问题标签": "、".join(issue_tags) if issue_tags else "",
            "逐行分析结论": build_row_conclusion(label_type, parsed),
            "修复后预期": expected_after_fix(label_type, parsed),
        })

    return pd.DataFrame(out_rows)


def build_summary(df_audit: pd.DataFrame) -> pd.DataFrame:
    def vc(col: str) -> pd.DataFrame:
        s = df_audit[col].astype(str).fillna("")
        return s.value_counts().rename_axis(col).reset_index(name="count")

    parts: List[Tuple[str, pd.DataFrame]] = []
    parts.append(("结论分布", vc("解析_结论")))
    parts.append(("后置验证状态分布", vc("解析_后置验证状态")))
    parts.append(("问题标签分布", vc("诊断_问题标签")))
    parts.append(("身份证前缀类型分布", vc("解析_身份证前缀类型")))
    parts.append(("信息缺失关键词分布", vc("解析_信息缺失关键词")))

    # Flatten to one sheet with section headers
    rows: List[Dict[str, Any]] = []
    for title, d in parts:
        rows.append({"统计项": title, "key": "", "count": ""})
        for _, r in d.iterrows():
            rows.append({"统计项": "", "key": r.iloc[0], "count": int(r["count"])})
        rows.append({"统计项": "", "key": "", "count": ""})

    return pd.DataFrame(rows)


def main() -> int:
    args = parse_args()
    task90_path = Path(args.task90)
    task91_path = Path(args.task91)

    if not task90_path.exists():
        raise FileNotFoundError(task90_path)
    if not task91_path.exists():
        raise FileNotFoundError(task91_path)

    df90 = pd.read_excel(task90_path, sheet_name=args.sheet)
    df91 = pd.read_excel(task91_path, sheet_name=args.sheet)

    # 基础校验：列存在
    for col in COL_COMMON + [COL_TASK90]:
        if col not in df90.columns:
            raise ValueError(f"Task-90 缺少列: {col}")
    for col in COL_COMMON + [COL_TASK91]:
        if col not in df91.columns:
            raise ValueError(f"Task-91 缺少列: {col}")

    audit90 = audit_one(df90, "在校学生", COL_TASK90)
    audit91 = audit_one(df91, "涉警当事人", COL_TASK91)

    ts = datetime.now().strftime("%Y%m%d%H%M")
    out_path = Path(args.out) if args.out else (Path("测试数据") / f"audit_task90_task91_{ts}.xlsx")

    with pd.ExcelWriter(out_path, engine="openpyxl") as w:
        audit90.to_excel(w, index=False, sheet_name="Task90_在校学生")
        audit91.to_excel(w, index=False, sheet_name="Task91_涉警当事人")
        build_summary(pd.concat([audit90, audit91], ignore_index=True)).to_excel(w, index=False, sheet_name="Summary")

    print(str(out_path))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
