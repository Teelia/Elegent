package com.datalabeling.service.extraction;

import com.datalabeling.entity.Label;
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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task43 回归：提取“不满足18位的错误身份证号”。
 *
 * <p>说明：该测试使用仓库内置的 xlsx 作为回归集（若文件缺失则自动跳过）。
 * 目标是避免出现历史上的系统性漏检（大量“未找到身份证号”）。
 */
public class NegativeConditionPreprocessorTask43Test {

    private static final String LABEL_DESC = "帮我提取不满足18位的错误的身份证号";
    private static final Pattern DIGITS_14_22 = Pattern.compile("\\d{14,22}");
    private static final Pattern ID_15_STRUCTURE = Pattern.compile("^[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}$");

    @Test
    void should_extract_invalid_id_length_even_when_bank_card_exists() {
        NegativeConditionPreprocessor pre = new NegativeConditionPreprocessor();

        Map<String, Object> row = new HashMap<>();
        row.put("测试数据", "反馈内容 银行卡号 6212261202004263290， 张三 34022319980605623");

        Label label = Label.builder().description(LABEL_DESC).build();
        NegativeConditionPreprocessor.PreprocessResult r = pre.preprocess(label, row);
        assertNotNull(r);
        assertTrue(r.canHandle());
        assertFalse(r.isEmpty(), "预期提取到17位错误身份证号");
        assertEquals("34022319980605623", r.getResult());
    }

    @Test
    void should_extract_19_digit_invalid_id_and_ignore_valid_18_digit_id() {
        NegativeConditionPreprocessor pre = new NegativeConditionPreprocessor();

        Map<String, Object> row = new HashMap<>();
        row.put("测试数据", "张三 3402231998060562312：...411327199511061118；银行卡 6217903600007663155");

        Label label = Label.builder().description(LABEL_DESC).build();
        NegativeConditionPreprocessor.PreprocessResult r = pre.preprocess(label, row);
        assertNotNull(r);
        assertTrue(r.canHandle());
        assertFalse(r.isEmpty(), "预期提取到19位错误身份证号");

        Set<String> got = splitToSet(r.getResult());
        assertTrue(got.contains("3402231998060562312"), "结果缺少19位候选");
        assertFalse(got.contains("411327199511061118"), "不应返回18位身份证号");
        assertFalse(got.contains("6217903600007663155"), "不应把银行卡号当成错误身份证号返回");
    }

    @Test
    void should_not_extract_valid_15_digit_id_as_invalid_length() {
        NegativeConditionPreprocessor pre = new NegativeConditionPreprocessor();

        Map<String, Object> row = new HashMap<>();
        row.put("测试数据", "报警人证件号 620525200906271");

        Label label = Label.builder().description(LABEL_DESC).build();
        NegativeConditionPreprocessor.PreprocessResult r = pre.preprocess(label, row);
        assertNotNull(r);
        assertTrue(r.canHandle());
        assertTrue(r.isEmpty(), "15位旧身份证视为正确，不应纳入“不满足18位的错误身份证号”");
        assertEquals("无", r.getResult());
    }

    @Test
    void regression_xlsx_should_not_have_systematic_false_negatives() throws Exception {
        Path xlsx = Paths.get("..", "测试数据", "analysis-task-43-results-with-reasoning.xlsx").normalize();
        Assumptions.assumeTrue(Files.exists(xlsx), "回归集文件不存在，跳过: " + xlsx.toString());

        NegativeConditionPreprocessor pre = new NegativeConditionPreprocessor();
        Label label = Label.builder().description(LABEL_DESC).build();

        try (InputStream in = Files.newInputStream(xlsx);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            int headerRowIdx = sheet.getFirstRowNum();
            Row header = sheet.getRow(headerRowIdx);
            assertNotNull(header, "xlsx 头行缺失");

            int colText = findColumnIndex(header, fmt, "测试数据");
            assertTrue(colText >= 0, "未找到列: 测试数据");

            int expectedPos = 0;
            int actualPos = 0;

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String text = fmt.formatCellValue(row.getCell(colText));
                if (text == null) {
                    text = "";
                }

                Set<String> expected = expectedInvalidIds(text);
                if (!expected.isEmpty()) {
                    expectedPos++;
                }

                Map<String, Object> rowData = new HashMap<>();
                rowData.put("测试数据", text);
                NegativeConditionPreprocessor.PreprocessResult r = pre.preprocess(label, rowData);
                assertNotNull(r, "预处理结果不应为null（该任务属于否定条件规则可处理范围）");

                boolean hit = !r.isEmpty() && r.getResult() != null && !r.getResult().trim().isEmpty() && !"无".equals(r.getResult().trim());
                if (hit) {
                    actualPos++;
                }

                // 重点：避免历史系统性漏检（存在候选却返回“无”）
                if (!expected.isEmpty()) {
                    final int rowIndex = i;
                    assertTrue(hit, () -> "回归漏检: 预期存在非18位身份证候选，但返回无；rowIndex=" + rowIndex +
                        ", expected(masked)=" + maskSet(expected));
                }
            }

            assertTrue(expectedPos > 0, "回归集中未发现任何预期命中样本，测试无意义");
            assertTrue(actualPos >= expectedPos, "命中数不足：actual=" + actualPos + ", expected=" + expectedPos);
        }
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

    private static Set<String> expectedInvalidIds(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return out;
        }

        Matcher matcher = DIGITS_14_22.matcher(text);
        while (matcher.find()) {
            String n = matcher.group();
            if (n == null) {
                continue;
            }
            // 15位旧身份证：视为正确，不纳入“不满足18位的错误身份证号”
            if (n.length() == 15 && ID_15_STRUCTURE.matcher(n).matches()) {
                continue;
            }
            if (n.length() != 18 && isIdLikeByAreaAndBirth(n)) {
                out.add(n);
            }
        }
        return out;
    }

    private static boolean isIdLikeByAreaAndBirth(String number) {
        if (number == null || number.length() < 14) {
            return false;
        }
        char first = number.charAt(0);
        if (first < '1' || first > '6') {
            return false;
        }
        for (int i = 0; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (ch < '0' || ch > '9') {
                return false;
            }
        }
        String birth = number.substring(6, 14);
        int year;
        int month;
        int day;
        try {
            year = Integer.parseInt(birth.substring(0, 4));
            month = Integer.parseInt(birth.substring(4, 6));
            day = Integer.parseInt(birth.substring(6, 8));
        } catch (NumberFormatException e) {
            return false;
        }
        int currentYear = LocalDate.now().getYear();
        if (year < 1900 || year > currentYear + 1) {
            return false;
        }
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private static Set<String> splitToSet(String value) {
        Set<String> set = new HashSet<>();
        if (value == null) {
            return set;
        }
        String[] parts = value.split("\\s*,\\s*");
        for (String p : parts) {
            if (p != null) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    set.add(s);
                }
            }
        }
        return set;
    }

    private static String mask(String n) {
        if (n == null) {
            return "null";
        }
        String s = n.trim();
        if (s.length() <= 8) {
            return s;
        }
        return s.substring(0, 4) + repeat('*', s.length() - 8) + s.substring(s.length() - 4);
    }

    private static String maskSet(Set<String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(mask(v));
        }
        return sb.toString();
    }

    private static String repeat(char ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
