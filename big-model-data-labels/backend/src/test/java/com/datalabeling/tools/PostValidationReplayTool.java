package com.datalabeling.tools;

import com.datalabeling.service.extraction.ExtractedNumber;
import com.datalabeling.service.extraction.PartyExtractor;
import com.datalabeling.util.PostProcessValidator;
import com.datalabeling.util.StudentInfoValidator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 离线重放后置验证（不连接数据库/不调用LLM）：
 * - 读取 results-with-reasoning.xlsx
 * - 从单元格文本中解析“后置验证前的判定（判定为[是]/[否]）”，并执行 PostProcessValidator.validate(...)
 * - 生成一个带对比列的新 xlsx，便于验证修复是否生效
 */
public class PostValidationReplayTool {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static void main(String[] args) throws Exception {
        // 默认路径
        String task90 = args.length > 0 ? args[0] : "../测试数据/analysis-task-90-results-with-reasoning (1).xlsx";
        String task91 = args.length > 1 ? args[1] : "../测试数据/analysis-task-91-results-with-reasoning.xlsx";

        String out = args.length > 2
            ? args[2]
            : "../测试数据/replay_post_validation_task90_task91_" + LocalDateTime.now().format(TS) + ".xlsx";

        Path outPath = Paths.get(out).normalize();
        Files.createDirectories(outPath.getParent());

        PostProcessValidator post = new PostProcessValidator(new PartyExtractor(), new StudentInfoValidator());
        List<ExtractedNumber> extractedNumbers = Collections.emptyList();

        try (Workbook outWb = new XSSFWorkbook()) {
            Sheet s90 = outWb.createSheet("Task90_在校学生");
            Sheet s91 = outWb.createSheet("Task91_涉警当事人");
            Sheet summary = outWb.createSheet("Summary");

            ReplayStats st90 = replayOne(task90, "在校学生信息完整性检查", "在校学生信息完整性检查_v1", post, extractedNumbers, s90);
            ReplayStats st91 = replayOne(task91, "涉警当事人信息完整性检查", "涉警当事人信息完整性检查_v1", post, extractedNumbers, s91);

            writeSummary(summary, st90, st91);

            try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                outWb.write(fos);
            }
        }

        System.out.println(outPath.toString());
    }

    private static ReplayStats replayOne(String inFile,
                                         String labelName,
                                         String labelCol,
                                         PostProcessValidator post,
                                         List<ExtractedNumber> extractedNumbers,
                                         Sheet outSheet) throws Exception {
        ReplayStats stats = new ReplayStats(labelName);

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(inFile))) {
            Sheet sheet = wb.getSheet("分析结果");
            if (sheet == null) {
                throw new IllegalArgumentException("缺少sheet: 分析结果, file=" + inFile);
            }

            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("缺少表头, file=" + inFile);
            }

            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (Cell c : header) {
                String name = getCellString(c);
                if (name != null && !name.trim().isEmpty()) {
                    colIndex.put(name.trim(), c.getColumnIndex());
                }
            }

            if (!colIndex.containsKey(labelCol)) {
                throw new IllegalArgumentException("缺少标签列: " + labelCol + ", file=" + inFile);
            }

            // 输出表头
            int outRowNum = 0;
            Row outHeader = outSheet.createRow(outRowNum++);
            List<String> outCols = Arrays.asList(
                "行号", "事件单编号", "接警单位", "所属分局",
                "反馈内容",
                "原始标签输出",
                "解析_结论(最终)", "解析_后置验证前结果",
                "重放_后置验证是否通过", "重放_后置验证级别", "重放_后置验证信息",
                "重放_预测最终结果", "重放_是否会修正", "重放_变化类型"
            );
            for (int i = 0; i < outCols.size(); i++) {
                outHeader.createCell(i).setCellValue(outCols.get(i));
            }

            // 行处理
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                Map<String, Object> rowData = new LinkedHashMap<>();
                String rawOutput = "";
                for (Map.Entry<String, Integer> e : colIndex.entrySet()) {
                    String k = e.getKey();
                    Cell c = row.getCell(e.getValue());
                    String v = getCellString(c);
                    if (labelCol.equals(k)) {
                        // 注意：后置验证应仅基于“原始数据”，不能把“标签输出（含校验信息）”拼进原文，否则会产生自污染误判。
                        rawOutput = v;
                        continue;
                    }
                    rowData.put(k, v);
                }
                String finalConclusion = parseConclusion(rawOutput);
                String prePost = parsePrePostResult(rawOutput, finalConclusion);

                PostProcessValidator.ValidationResult v =
                    post.validate(prePost, rowData, extractedNumbers, labelName);

                boolean willAdjust = !v.isValid();
                String predictedFinal = willAdjust ? "否" : prePost;

                String changeType = classifyChange(rawOutput, v, finalConclusion, prePost, predictedFinal);

                // stats
                stats.total++;
                if ("是".equals(finalConclusion)) stats.finalYes++;
                if ("否".equals(finalConclusion)) stats.finalNo++;
                if (v.isValid()) stats.replayValid++; else stats.replayInvalid++;
                if (willAdjust) stats.replayAdjusted++;
                stats.changeTypes.merge(changeType, 1, Integer::sum);

                Row outRow = outSheet.createRow(outRowNum++);
                int c = 0;
                outRow.createCell(c++).setCellValue(asString(rowData.get("行号")));
                outRow.createCell(c++).setCellValue(asString(rowData.get("事件单编号")));
                outRow.createCell(c++).setCellValue(asString(rowData.get("接警单位")));
                outRow.createCell(c++).setCellValue(asString(rowData.get("所属分局")));
                outRow.createCell(c++).setCellValue(asString(rowData.get("反馈内容")));
                outRow.createCell(c++).setCellValue(rawOutput);
                outRow.createCell(c++).setCellValue(finalConclusion);
                outRow.createCell(c++).setCellValue(prePost);
                outRow.createCell(c++).setCellValue(v.isValid() ? "通过" : "失败");
                outRow.createCell(c++).setCellValue(v.getLevel() != null ? v.getLevel().name() : "");
                outRow.createCell(c++).setCellValue(v.getMessage() != null ? v.getMessage() : "");
                outRow.createCell(c++).setCellValue(predictedFinal);
                outRow.createCell(c++).setCellValue(willAdjust ? "是" : "否");
                outRow.createCell(c++).setCellValue(changeType);
            }
        }

        // 自动列宽（前10列）
        int max = Math.min(10, outSheet.getRow(0).getLastCellNum());
        for (int i = 0; i < max; i++) {
            outSheet.autoSizeColumn(i);
        }
        return stats;
    }

    private static String classifyChange(String rawOutput,
                                         PostProcessValidator.ValidationResult replay,
                                         String finalConclusion,
                                         String prePost,
                                         String predictedFinal) {
        // 变化类型用于筛选：修复后“从会被修正为否”变为“通过不修正”等
        boolean oldHadPrefixError = rawOutput != null && rawOutput.contains("身份证号前存在");
        boolean oldHadPoliceParty = rawOutput != null && rawOutput.contains("涉警当事人共");

        if (oldHadPrefixError && replay.isValid()) {
            return "修复影响:身份证前缀误判消失";
        }
        if (oldHadPoliceParty && replay.isValid()) {
            return "修复影响:涉警当事人缺失不再触发(或被降噪)";
        }
        if (!Objects.equals(finalConclusion, predictedFinal)) {
            return "修复影响:预测最终结论变化";
        }
        if (!replay.isValid()) {
            return "仍失败:需要人工复核/规则继续优化";
        }
        return "无变化";
    }

    private static void writeSummary(Sheet sheet, ReplayStats... stats) {
        int r = 0;
        Row h = sheet.createRow(r++);
        h.createCell(0).setCellValue("标签");
        h.createCell(1).setCellValue("总行数");
        h.createCell(2).setCellValue("原最终_是");
        h.createCell(3).setCellValue("原最终_否");
        h.createCell(4).setCellValue("重放_通过");
        h.createCell(5).setCellValue("重放_失败");
        h.createCell(6).setCellValue("重放_会修正");

        for (ReplayStats st : stats) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(st.label);
            row.createCell(1).setCellValue(st.total);
            row.createCell(2).setCellValue(st.finalYes);
            row.createCell(3).setCellValue(st.finalNo);
            row.createCell(4).setCellValue(st.replayValid);
            row.createCell(5).setCellValue(st.replayInvalid);
            row.createCell(6).setCellValue(st.replayAdjusted);
        }

        r++;
        Row h2 = sheet.createRow(r++);
        h2.createCell(0).setCellValue("变化类型");
        h2.createCell(1).setCellValue("标签");
        h2.createCell(2).setCellValue("count");

        for (ReplayStats st : stats) {
            for (Map.Entry<String, Integer> e : st.changeTypes.entrySet()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getKey());
                row.createCell(1).setCellValue(st.label);
                row.createCell(2).setCellValue(e.getValue());
            }
        }
    }

    private static String parseConclusion(String raw) {
        if (raw == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("结论[:：]\\s*([是否])").matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        String t = raw.trim();
        if ("是".equals(t) || "否".equals(t)) {
            return t;
        }
        return "";
    }

    private static String parsePrePostResult(String raw, String fallback) {
        if (raw == null) {
            return fallback != null ? fallback : "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("判定为\\[([是否])\\]").matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return fallback != null ? fallback : "";
    }

    private static String getCellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        try {
            if (cell.getCellType() == CellType.STRING) {
                return cell.getStringCellValue();
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                // 避免科学计数法/小数尾巴：尽量按 long 输出
                long l = (long) d;
                if (Math.abs(d - l) < 1e-9) {
                    return String.valueOf(l);
                }
                return String.valueOf(d);
            }
            if (cell.getCellType() == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
            if (cell.getCellType() == CellType.FORMULA) {
                try {
                    return cell.getStringCellValue();
                } catch (Exception ignore) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ignore2) {
                        return cell.getCellFormula();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static class ReplayStats {
        final String label;
        int total;
        int finalYes;
        int finalNo;
        int replayValid;
        int replayInvalid;
        int replayAdjusted;
        final Map<String, Integer> changeTypes = new LinkedHashMap<>();

        ReplayStats(String label) {
            this.label = label;
        }
    }
}
