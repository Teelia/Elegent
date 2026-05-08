package com.datalabeling.service.extraction;

import com.datalabeling.dto.PreprocessorConfig;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 手机号/银行卡 number_intent 回归测试。
 *
 * <p>说明：
 * - 使用仓库内置 xlsx 作为回归集（若文件缺失则跳过）
 * - 目标是验证“存在/提取/无效/遮挡”四类意图的规则闭环输出
 */
public class NumberIntentEvaluatorPhoneBankRegressionTest {

    @Test
    void regression_xlsx_should_match_expected_phone_and_bank_intents() throws Exception {
        Path xlsx = Paths.get("..", "测试数据", "analysis-task-45-phone-bank-results.xlsx").normalize();
        Assumptions.assumeTrue(Files.exists(xlsx), "回归集文件不存在，跳过: " + xlsx.toString());

        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        try (InputStream in = Files.newInputStream(xlsx);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            int headerRowIdx = sheet.getFirstRowNum();
            Row header = sheet.getRow(headerRowIdx);
            assertNotNull(header, "xlsx 头行缺失");

            int colText = findColumnIndex(header, fmt, "测试数据");
            int colPhoneExists = findColumnIndex(header, fmt, "phone_exists");
            int colPhoneExtract = findColumnIndex(header, fmt, "phone_extract");
            int colPhoneInvalid = findColumnIndex(header, fmt, "phone_invalid");
            int colPhoneMasked = findColumnIndex(header, fmt, "phone_masked");
            int colBankExists = findColumnIndex(header, fmt, "bank_exists");
            int colBankExtract = findColumnIndex(header, fmt, "bank_extract");
            int colBankInvalid = findColumnIndex(header, fmt, "bank_invalid");
            int colBankMasked = findColumnIndex(header, fmt, "bank_masked");

            assertTrue(colText >= 0, "未找到列: 测试数据");
            assertTrue(colPhoneExists >= 0, "未找到列: phone_exists");
            assertTrue(colPhoneExtract >= 0, "未找到列: phone_extract");
            assertTrue(colPhoneInvalid >= 0, "未找到列: phone_invalid");
            assertTrue(colPhoneMasked >= 0, "未找到列: phone_masked");
            assertTrue(colBankExists >= 0, "未找到列: bank_exists");
            assertTrue(colBankExtract >= 0, "未找到列: bank_extract");
            assertTrue(colBankInvalid >= 0, "未找到列: bank_invalid");
            assertTrue(colBankMasked >= 0, "未找到列: bank_masked");

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String text = fmt.formatCellValue(row.getCell(colText));
                if (text == null) {
                    text = "";
                }

                // phone
                assertEquals(fmt.formatCellValue(row.getCell(colPhoneExists)).trim(),
                    eval(evaluator, text, "phone", "exists").getSummary(),
                    "phone_exists 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colPhoneExtract))),
                    actualSet(eval(evaluator, text, "phone", "extract").getSummary()),
                    "phone_extract 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colPhoneInvalid))),
                    actualSet(eval(evaluator, text, "phone", "invalid").getSummary()),
                    "phone_invalid 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colPhoneMasked))),
                    actualSet(eval(evaluator, text, "phone", "masked").getSummary()),
                    "phone_masked 不一致: rowIndex=" + i);

                // bank
                NumberIntentEvaluator.EvaluationResult bankExists = eval(evaluator, text, "bank_card", "exists");
                assertEquals(fmt.formatCellValue(row.getCell(colBankExists)).trim(),
                    bankExists.getSummary(),
                    "bank_exists 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colBankExtract))),
                    actualSet(eval(evaluator, text, "bank_card", "extract").getSummary()),
                    "bank_extract 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colBankInvalid))),
                    actualSet(eval(evaluator, text, "bank_card", "invalid").getSummary()),
                    "bank_invalid 不一致: rowIndex=" + i);

                assertSetEquals(expectedSet(fmt.formatCellValue(row.getCell(colBankMasked))),
                    actualSet(eval(evaluator, text, "bank_card", "masked").getSummary()),
                    "bank_masked 不一致: rowIndex=" + i);

                // 特殊：无效银行卡无关键词时，exists 允许以“弱证据”命中但 needs_review=true
                if (text.contains("号码") && text.contains("6212261202004263291")) {
                    assertNotNull(bankExists.getExtractedData(), "extractedData 不应为null");
                    Object nr = bankExists.getExtractedData().get("needs_review");
                    assertTrue(nr instanceof Boolean && (Boolean) nr, "该行应标记 needs_review=true: rowIndex=" + i);
                }
            }
        }
    }

    private static NumberIntentEvaluator.EvaluationResult eval(NumberIntentEvaluator evaluator, String text, String entity, String task) {
        String json = "{"
            + "\"extractors\":[],"
            + "\"extractorOptions\":{\"_ignored\":{}},"
            + "\"number_intent\":{"
            + "\"entity\":\"" + entity + "\","
            + "\"task\":\"" + task + "\","
            + "\"include\":[\"valid\",\"invalid\",\"masked\"],"
            + "\"policy\":{\"require_keyword_for_invalid_bank\":true}"
            + "}"
            + "}";
        PreprocessorConfig cfg = PreprocessorConfig.fromJson(json);
        assertNotNull(cfg.getNumberIntent(), "number_intent 解析失败");
        return evaluator.evaluate(text, cfg.getNumberIntent());
    }

    private static int findColumnIndex(Row header, DataFormatter fmt, String columnName) {
        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            String v = fmt.formatCellValue(header.getCell(c));
            if (columnName.equals(v)) {
                return c;
            }
        }
        return -1;
    }

    private static Set<String> expectedSet(String s) {
        return splitListCell(s);
    }

    private static Set<String> actualSet(String summary) {
        if (summary == null) {
            return new HashSet<>();
        }
        String v = summary.trim();
        if (v.isEmpty() || "无".equals(v)) {
            return new HashSet<>();
        }
        // exists 类型返回 是/否，不作为集合比较
        if ("是".equals(v) || "否".equals(v)) {
            return new HashSet<>();
        }
        return splitListCell(v);
    }

    private static Set<String> splitListCell(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) {
            return out;
        }
        String v = s.trim();
        if (v.isEmpty() || "无".equals(v)) {
            return out;
        }
        // 兼容中文逗号、英文逗号、空白
        String[] parts = v.split("[，,\\s]+");
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String t = p.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static void assertSetEquals(Set<String> expected, Set<String> actual, String msg) {
        assertEquals(expected, actual, msg + " expected=" + expected + ", actual=" + actual);
    }
}

