#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对比：旧导出结果（修复前） vs 新导出结果（修复后重跑）

用途：
  - 快速定位“结论/后置验证”发生变化的行
  - 为逐行核查提供一份“旧-新并排”视图

默认输入（可用参数覆盖）：
  - 旧Task90：测试数据/analysis-task-90-results-with-reasoning (1).xlsx
  - 旧Task91：测试数据/analysis-task-91-results-with-reasoning.xlsx
  - 新Task90：需显式传入（建议用最新导出文件）
  - 新Task91：需显式传入

输出：
  - 测试数据/compare_old_vs_new_<timestamp>.xlsx
"""

from __future__ import annotations

import argparse
import re
from datetime import datetime
from pathlib import Path
from typing import Any, Optional, Dict, List, Tuple

import pandas as pd


SHEET_NAME = "分析结果"

OLD90_DEFAULT = Path(r"测试数据/analysis-task-90-results-with-reasoning (1).xlsx")
OLD91_DEFAULT = Path(r"测试数据/analysis-task-91-results-with-reasoning.xlsx")

COL_COMMON = ["行号", "事件单编号", "接警单位", "所属分局", "反馈内容"]
COL_STUDENT = "在校学生信息完整性检查_v1"
COL_PARTY = "涉警当事人信息完整性检查_v1"


def _s(x: Any) -> str:
    return "" if x is None else str(x)


def parse_conclusion(text: Any) -> Optional[str]:
    t = _s(text)
    m = re.search(r"结论[:：]\s*([是否])", t)
    if m:
        return m.group(1)
    t2 = t.strip()
    if t2 in ("是", "否"):
        return t2
    return None


def parse_post_status(text: Any) -> Optional[str]:
    t = _s(text)
    if "后置规则验证通过" in t:
        return "通过"
    if "后置规则验证:" in t or "后置规则验证：" in t or "后置验证失败" in t:
        return "失败"
    return None


def key_of(df: pd.DataFrame) -> pd.Series:
    # join key: 事件单编号（更稳定），若缺失则回退行号
    if "事件单编号" in df.columns:
        k = df["事件单编号"].astype(str)
        return k.where(k.notna() & (k != "nan") & (k != ""), df["行号"].astype(str))
    return df["行号"].astype(str)


def load_xlsx(path: Path, label_col: str, sheet: str) -> pd.DataFrame:
    df = pd.read_excel(path, sheet_name=sheet)
    need = COL_COMMON + [label_col]
    for c in need:
        if c not in df.columns:
            raise ValueError(f"{path} 缺少列: {c}")
    df = df[need].copy()
    df["_k"] = key_of(df)
    df["解析_结论"] = df[label_col].map(parse_conclusion)
    df["解析_后置验证状态"] = df[label_col].map(parse_post_status)
    df.rename(columns={label_col: "原始标签输出"}, inplace=True)
    return df


def merge_old_new(old_df: pd.DataFrame, new_df: pd.DataFrame) -> pd.DataFrame:
    o = old_df.copy()
    n = new_df.copy()
    o = o.add_prefix("旧_")
    n = n.add_prefix("新_")
    m = o.merge(n, left_on="旧__k", right_on="新__k", how="outer", suffixes=("", ""))

    # 统一key
    m["_k"] = m["旧__k"].where(m["旧__k"].notna(), m["新__k"])

    m["对比_结论变化"] = (m["旧_解析_结论"].astype(str) != m["新_解析_结论"].astype(str)) & m["旧_解析_结论"].notna() & m["新_解析_结论"].notna()
    m["对比_后置验证变化"] = (m["旧_解析_后置验证状态"].astype(str) != m["新_解析_后置验证状态"].astype(str)) & m["旧_解析_后置验证状态"].notna() & m["新_解析_后置验证状态"].notna()
    m["对比_旧有身份证前缀报错_新已消失"] = m["旧_原始标签输出"].astype(str).str.contains("身份证号前存在", na=False) & ~m["新_原始标签输出"].astype(str).str.contains("身份证号前存在", na=False)

    # 便于人工核查：常用列前置
    front = [
        "_k",
        "旧_行号", "旧_事件单编号", "旧_接警单位", "旧_所属分局", "旧_反馈内容",
        "旧_解析_结论", "旧_解析_后置验证状态", "旧_原始标签输出",
        "新_行号", "新_事件单编号", "新_接警单位", "新_所属分局", "新_反馈内容",
        "新_解析_结论", "新_解析_后置验证状态", "新_原始标签输出",
        "对比_结论变化", "对比_后置验证变化", "对比_旧有身份证前缀报错_新已消失",
    ]
    cols = [c for c in front if c in m.columns] + [c for c in m.columns if c not in front]
    return m[cols]


def build_summary(name: str, df: pd.DataFrame) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    total = len(df)
    rows.append({"模块": name, "统计项": "总行数", "值": total})
    for col in ("对比_结论变化", "对比_后置验证变化", "对比_旧有身份证前缀报错_新已消失"):
        if col in df.columns:
            rows.append({"模块": name, "统计项": col, "值": int(df[col].fillna(False).astype(bool).sum())})
    return rows


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="对比旧-新导出结果（含推理内容）")
    p.add_argument("--old90", type=str, default=str(OLD90_DEFAULT), help="旧Task90导出xlsx")
    p.add_argument("--old91", type=str, default=str(OLD91_DEFAULT), help="旧Task91导出xlsx")
    p.add_argument("--new90", type=str, required=True, help="新Task90导出xlsx（修复后重跑）")
    p.add_argument("--new91", type=str, required=True, help="新Task91导出xlsx（修复后重跑）")
    p.add_argument("--sheet", type=str, default=SHEET_NAME, help="工作表名称（默认：分析结果）")
    p.add_argument("--out", type=str, default="", help="输出xlsx路径（默认写入测试数据/compare_old_vs_new_<ts>.xlsx）")
    return p.parse_args()


def main() -> int:
    args = parse_args()
    old90 = Path(args.old90)
    old91 = Path(args.old91)
    new90 = Path(args.new90)
    new91 = Path(args.new91)

    for p in (old90, old91, new90, new91):
        if not p.exists():
            raise FileNotFoundError(p)

    o90 = load_xlsx(old90, COL_STUDENT, args.sheet)
    n90 = load_xlsx(new90, COL_STUDENT, args.sheet)
    o91 = load_xlsx(old91, COL_PARTY, args.sheet)
    n91 = load_xlsx(new91, COL_PARTY, args.sheet)

    m90 = merge_old_new(o90, n90)
    m91 = merge_old_new(o91, n91)

    ts = datetime.now().strftime("%Y%m%d%H%M")
    out = Path(args.out) if args.out else (Path("测试数据") / f"compare_old_vs_new_{ts}.xlsx")

    summary_rows: List[Dict[str, Any]] = []
    summary_rows += build_summary("在校学生", m90)
    summary_rows += build_summary("涉警当事人", m91)
    summary = pd.DataFrame(summary_rows)

    with pd.ExcelWriter(out, engine="openpyxl") as w:
        m90.to_excel(w, index=False, sheet_name="Task90_在校学生")
        m91.to_excel(w, index=False, sheet_name="Task91_涉警当事人")
        summary.to_excel(w, index=False, sheet_name="Summary")

    print(str(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

