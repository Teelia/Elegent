package com.datalabeling.service;

import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.Dataset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据导出服务
 * 支持导出数据集为 Excel (.xlsx) 或 CSV 格式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final DatasetService datasetService;

    /**
     * 导出数据集为 Excel 格式
     */
    public void exportToExcel(Integer datasetId, HttpServletResponse response) throws IOException {
        Dataset dataset = datasetService.getDatasetEntity(datasetId);
        List<DataRow> dataRows = datasetService.getAllDataRows(datasetId);

        // 获取列名
        List<String> columnNames = extractColumnNames(dataset);

        // 设置响应头
        String filename = getExportFilename(dataset, "xlsx");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写入表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnNames.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 写入数据行
            int rowNum = 1;
            for (DataRow dataRow : dataRows) {
                Row row = sheet.createRow(rowNum++);
                Map<String, Object> originalData = dataRow.getOriginalData();
                for (int i = 0; i < columnNames.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = originalData.get(columnNames.get(i));
                    if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            // 自动调整列宽
            for (int i = 0; i < columnNames.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入响应
            workbook.write(response.getOutputStream());
        }

        log.info("导出数据集 {} 为 Excel，共 {} 行", dataset.getName(), dataRows.size());
    }

    /**
     * 导出数据集为 CSV 格式
     */
    public void exportToCsv(Integer datasetId, HttpServletResponse response) throws IOException {
        Dataset dataset = datasetService.getDatasetEntity(datasetId);
        List<DataRow> dataRows = datasetService.getAllDataRows(datasetId);

        // 获取列名
        List<String> columnNames = extractColumnNames(dataset);

        // 设置响应头
        String filename = getExportFilename(dataset, "csv");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // 添加 UTF-8 BOM，确保 Excel 正确识别编码
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            // 写入表头
            writer.println(toCsvLine(columnNames));

            // 写入数据行
            for (DataRow dataRow : dataRows) {
                Map<String, Object> originalData = dataRow.getOriginalData();
                List<String> values = new ArrayList<>();
                for (String colName : columnNames) {
                    Object value = originalData.get(colName);
                    values.add(value != null ? value.toString() : "");
                }
                writer.println(toCsvLine(values));
            }
        }

        log.info("导出数据集 {} 为 CSV，共 {} 行", dataset.getName(), dataRows.size());
    }

    /**
     * 从数据集获取列名列表
     */
    private List<String> extractColumnNames(Dataset dataset) {
        List<String> columnNames = new ArrayList<>();
        if (dataset.getColumns() != null) {
            for (Map<String, Object> col : dataset.getColumns()) {
                String name = (String) col.get("name");
                if (name != null) {
                    columnNames.add(name);
                }
            }
        }
        return columnNames;
    }

    /**
     * 生成导出文件名
     */
    private String getExportFilename(Dataset dataset, String extension) {
        String baseName = dataset.getName();
        if (baseName == null || baseName.isEmpty()) {
            baseName = "dataset_" + dataset.getId();
        }
        // 移除文件名中的非法字符
        baseName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            return URLEncoder.encode(baseName + "." + extension, StandardCharsets.UTF_8.name())
                    .replace("+", "%20");
        } catch (Exception e) {
            return "export." + extension;
        }
    }

    /**
     * 将字符串列表转换为 CSV 行
     */
    private String toCsvLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeCsvField(values.get(i)));
        }
        return sb.toString();
    }

    /**
     * 转义 CSV 字段
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // 如果字段包含逗号、引号或换行符，需要用引号包裹
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            // 转义内部的引号
            field = field.replace("\"", "\"\"");
            return "\"" + field + "\"";
        }
        return field;
    }
}
