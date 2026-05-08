#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对比：旧审计（基于原 results-with-reasoning 输出文本） vs 离线重放后置验证（基于修复后的 Java 规则）

输入：
  - 测试数据/audit_task90_task91_*.xlsx（逐行归因审计）
  - 测试数据/replay_post_validation_task90_task91_*.xlsx（离线重放后置验证）

输出：
  - 测试数据/compare_task90_task91_<timestamp>.xlsx
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path

import pandas as pd


def newest(pattern: str) -> Path:
    files = sorted(Path("测试数据").glob(pattern), key=lambda p: p.stat().st_mtime, reverse=True)
    if not files:
        raise FileNotFoundError(f"未找到: 测试数据/{pattern}")
    return files[0]


def main() -> int:
    audit = newest("audit_task90_task91_*.xlsx")
    replay = newest("replay_post_validation_task90_task91_*.xlsx")

    ts = datetime.now().strftime("%Y%m%d%H%M")
    out = Path("测试数据") / f"compare_task90_task91_{ts}.xlsx"

    a90 = pd.read_excel(audit, sheet_name="Task90_在校学生")
    a91 = pd.read_excel(audit, sheet_name="Task91_涉警当事人")
    r90 = pd.read_excel(replay, sheet_name="Task90_在校学生")
    r91 = pd.read_excel(replay, sheet_name="Task91_涉警当事人")

    # join key: 事件单编号（更稳定），若缺失则回退行号
    def key(df: pd.DataFrame) -> pd.Series:
        k = df.get("事件单编号")
        if k is None:
            return df["行号"].astype(str)
        k = k.astype(str)
        return k.where(k.notna() & (k != "nan") & (k != ""), df["行号"].astype(str))

    a90["_k"] = key(a90)
    a91["_k"] = key(a91)
    r90["_k"] = key(r90)
    r91["_k"] = key(r91)

    def merge(a: pd.DataFrame, r: pd.DataFrame) -> pd.DataFrame:
        keep_a = [
            "_k", "行号", "事件单编号", "接警单位", "所属分局", "反馈内容",
            "解析_结论", "解析_后置验证状态",
            "解析_身份证前缀类型", "解析_信息缺失关键词", "诊断_问题标签",
            "逐行分析结论", "修复后预期",
        ]
        keep_r = [
            "_k",
            "解析_结论(最终)", "解析_后置验证前结果",
            "重放_后置验证是否通过", "重放_后置验证级别", "重放_后置验证信息",
            "重放_预测最终结果", "重放_是否会修正", "重放_变化类型",
        ]
        aa = a[[c for c in keep_a if c in a.columns]].copy()
        rr = r[[c for c in keep_r if c in r.columns]].copy()
        m = aa.merge(rr, on="_k", how="left", suffixes=("", "_replay"))

        # 便于筛选：是否修复了“身份证空白前缀误判”
        m["对比_空白前缀误判已消失"] = (
            (m.get("解析_身份证前缀类型") == "空白") &
            (m.get("重放_变化类型").fillna("").astype(str).str.contains("身份证前缀误判消失"))
        )
        return m

    out90 = merge(a90, r90)
    out91 = merge(a91, r91)

    with pd.ExcelWriter(out, engine="openpyxl") as w:
        out90.to_excel(w, index=False, sheet_name="Compare_Task90")
        out91.to_excel(w, index=False, sheet_name="Compare_Task91")

        meta = pd.DataFrame([{
            "audit_file": str(audit),
            "replay_file": str(replay),
            "generated_at": ts,
        }])
        meta.to_excel(w, index=False, sheet_name="Meta")

    print(str(out))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

