package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.entity.DataRow;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.service.model.FilePreviewResult;
import com.datalabeling.util.CharsetDetectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文件解析服务（Excel/CSV）
 */
@Slf4j
@Service
public class FileParseService {

    private static final int DEFAULT_SHEET_INDEX = 0;
    private static final char UTF8_BOM = '\uFEFF';

    /**
     * 从上传文件解析数据（不保存文件到磁盘）
     * 返回列信息、预览数据和所有数据行
     */
    public FilePreviewResult parseUploadedFile(MultipartFile file, int previewSize) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "文件名为空");
        }

        String lower = originalFilename.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".csv")) {
                return parseCsvFromStream(file.getInputStream(), previewSize);
            }
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return parseExcelFromStream(file.getInputStream(), previewSize);
            }
        } catch (IOException e) {
            log.error("读取上传文件失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "读取文件失败");
        }

        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "不支持的文件类型，仅支持 .csv, .xlsx, .xls");
    }

    /**
     * 从 CSV 流解析数据
     */
    private FilePreviewResult parseCsvFromStream(InputStream inputStream, int previewSize) {
        // 检测字符编码
        Charset charset = CharsetDetectorUtil.detectCharset(inputStream);
        log.info("检测到CSV文件编码: {}", charset);

        List<String> columns;
        List<Map<String, Object>> preview = new ArrayList<>();
        List<DataRow> dataRows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, charset)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV文件为空");
            }

            List<String> rawHeader = parseCsvLine(headerLine);
            if (rawHeader.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV表头为空");
            }
            rawHeader = removeBomFromFirstField(rawHeader);
            columns = makeUniqueColumns(normalizeColumns(rawHeader));

            int rowIndex = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                // 如果数据行列数超过表头，自动扩展列
                if (fields.size() > columns.size()) {
                    columns = expandColumns(columns, fields.size());
                }

                Map<String, Object> row = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < fields.size() ? fields.get(i) : "";
                    if (!value.trim().isEmpty()) {
                        allBlank = false;
                    }
                    row.put(columns.get(i), value);
                }

                if (allBlank) {
                    continue;
                }

                // 添加预览数据
                if (preview.size() < previewSize) {
                    preview.add(row);
                }

                // 构建 DataRow
                DataRow dataRow = DataRow.builder()
                    .rowIndex(rowIndex)
                    .originalData(row)
                    .processingStatus("pending")
                    .needsReview(false)
                    .isModified(false)
                    .build();
                dataRows.add(dataRow);
                rowIndex++;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("CSV解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV解析失败");
        }

        // 构建列信息
        List<Map<String, Object>> columnsInfo = buildColumnsInfo(columns);

        return FilePreviewResult.builder()
            .columns(columns)
            .columnsInfo(columnsInfo)
            .preview(preview)
            .totalRows(dataRows.size())
            .dataRows(dataRows)
            .build();
    }

    /**
     * 从 Excel 流解析数据
     */
    private FilePreviewResult parseExcelFromStream(InputStream inputStream, int previewSize) {
        List<String> columns;
        List<Map<String, Object>> preview = new ArrayList<>();
        List<DataRow> dataRows = new ArrayList<>();

        // 使用 DataFormatter 处理中文（设置 Locale 为 China）
        DataFormatter formatter = new DataFormatter(java.util.Locale.CHINA);

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {

            if (workbook.getNumberOfSheets() <= DEFAULT_SHEET_INDEX) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel无有效工作表");
            }

            Sheet sheet = workbook.getSheetAt(DEFAULT_SHEET_INDEX);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            short lastCellNum = headerRow.getLastCellNum();
            if (lastCellNum <= 0) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            List<String> rawColumns = new ArrayList<>(lastCellNum);
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String col = formatter.formatCellValue(cell);
                rawColumns.add(col);
            }
            columns = makeUniqueColumns(normalizeColumns(rawColumns));

            int rowIndex = 0;
            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row rowObj = sheet.getRow(r);
                Map<String, Object> row = new LinkedHashMap<>();

                boolean allBlank = true;
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = rowObj != null
                        ? rowObj.getCell(c, MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        : null;
                    String value = cell != null ? formatter.formatCellValue(cell) : "";
                    if (!value.trim().isEmpty()) {
                        allBlank = false;
                    }
                    row.put(columns.get(c), value);
                }

                if (allBlank) {
                    continue;
                }

                // 添加预览数据
                if (preview.size() < previewSize) {
                    preview.add(row);
                }

                // 构建 DataRow
                DataRow dataRow = DataRow.builder()
                    .rowIndex(rowIndex)
                    .originalData(row)
                    .processingStatus("pending")
                    .needsReview(false)
                    .isModified(false)
                    .build();
                dataRows.add(dataRow);
                rowIndex++;
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel解析失败");
        }

        // 构建列信息
        List<Map<String, Object>> columnsInfo = buildColumnsInfo(columns);

        return FilePreviewResult.builder()
            .columns(columns)
            .columnsInfo(columnsInfo)
            .preview(preview)
            .totalRows(dataRows.size())
            .dataRows(dataRows)
            .build();
    }

    /**
     * 构建列信息
     */
    private List<Map<String, Object>> buildColumnsInfo(List<String> columns) {
        List<Map<String, Object>> columnsInfo = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> colInfo = new HashMap<>();
            colInfo.put("index", i);
            colInfo.put("name", columns.get(i));
            colInfo.put("dataType", "文本");
            colInfo.put("nonNullRate", 100);
            columnsInfo.add(colInfo);
        }
        return columnsInfo;
    }

    // ==================== 以下为兼容旧代码的方法 ====================

    /**
     * 解析文件列与预览数据（从文件路径）
     * @deprecated 建议使用 parseUploadedFile(MultipartFile, int) 代替
     */
    @Deprecated
    public FilePreviewResult parsePreview(String filePath, int previewSize) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsvPreview(filePath, previewSize);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcelPreview(filePath, previewSize);
        }
        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "不支持的文件类型");
    }

    /**
     * 遍历所有数据行（从文件路径）
     * @deprecated 建议使用 parseUploadedFile(MultipartFile, int) 代替
     */
    @Deprecated
    public void forEachDataRow(String filePath, DataRowConsumer consumer) {
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            forEachCsvDataRow(filePath, consumer);
            return;
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            forEachExcelDataRow(filePath, consumer);
            return;
        }
        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORT, "不支持的文件类型");
    }

    @FunctionalInterface
    public interface DataRowConsumer {
        void accept(int rowIndex, Map<String, Object> rowData);
    }

    private FilePreviewResult parseCsvPreview(String filePath, int previewSize) {
        if (!Files.exists(Paths.get(filePath))) {
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "文件不存在");
        }

        // 检测字符编码
        Charset charset = StandardCharsets.UTF_8;  // 默认值
        try (InputStream in = new FileInputStream(filePath)) {
            charset = CharsetDetectorUtil.detectCharset(in);
            log.info("检测到CSV文件编码: {}", charset);
        } catch (IOException e) {
            log.warn("编码检测失败，使用默认UTF-8: {}", e.getMessage());
        }

        List<String> columns;
        List<Map<String, Object>> preview = new ArrayList<>();
        int totalRows = 0;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(filePath), charset)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV文件为空");
            }

            List<String> rawHeader = parseCsvLine(headerLine);
            if (rawHeader.isEmpty()) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV表头为空");
            }
            rawHeader = removeBomFromFirstField(rawHeader);
            columns = makeUniqueColumns(normalizeColumns(rawHeader));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                // 如果数据行列数超过表头，自动扩展列
                if (fields.size() > columns.size()) {
                    columns = expandColumns(columns, fields.size());
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < fields.size() ? fields.get(i) : "";
                    row.put(columns.get(i), value);
                }

                totalRows++;
                if (preview.size() < previewSize) {
                    preview.add(row);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("CSV解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV解析失败");
        }

        return FilePreviewResult.builder()
            .columns(columns)
            .preview(preview)
            .totalRows(totalRows)
            .build();
    }

    private void forEachCsvDataRow(String filePath, DataRowConsumer consumer) {
        // 检测字符编码
        Charset charset = StandardCharsets.UTF_8;  // 默认值
        try (InputStream in = new FileInputStream(filePath)) {
            charset = CharsetDetectorUtil.detectCharset(in);
            log.info("检测到CSV文件编码: {}", charset);
        } catch (IOException e) {
            log.warn("编码检测失败，使用默认UTF-8: {}", e.getMessage());
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(filePath), charset)
        )) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV文件为空");
            }

            List<String> rawHeader = removeBomFromFirstField(parseCsvLine(headerLine));
            List<String> columns = makeUniqueColumns(normalizeColumns(rawHeader));

            int rowIndex = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> fields = parseCsvLine(line);
                // 如果数据行列数超过表头，自动扩展列
                if (fields.size() > columns.size()) {
                    columns = expandColumns(columns, fields.size());
                }

                Map<String, Object> row = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < fields.size() ? fields.get(i) : "";
                    if (!value.trim().isEmpty()) {
                        allBlank = false;
                    }
                    row.put(columns.get(i), value);
                }
                if (allBlank) {
                    continue;
                }
                rowIndex++;
                consumer.accept(rowIndex, row);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("CSV解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "CSV解析失败");
        }
    }

    private FilePreviewResult parseExcelPreview(String filePath, int previewSize) {
        if (!Files.exists(Paths.get(filePath))) {
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "文件不存在");
        }

        List<String> columns;
        List<Map<String, Object>> preview = new ArrayList<>();
        int totalRows = 0;

        // 使用 DataFormatter 处理中文（设置 Locale 为 China）
        DataFormatter formatter = new DataFormatter(java.util.Locale.CHINA);

        try (InputStream in = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(in)) {

            if (workbook.getNumberOfSheets() <= DEFAULT_SHEET_INDEX) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel无有效工作表");
            }

            Sheet sheet = workbook.getSheetAt(DEFAULT_SHEET_INDEX);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            short lastCellNum = headerRow.getLastCellNum();
            if (lastCellNum <= 0) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            List<String> rawColumns = new ArrayList<>(lastCellNum);
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String col = formatter.formatCellValue(cell);
                rawColumns.add(col);
            }
            columns = makeUniqueColumns(normalizeColumns(rawColumns));

            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row rowObj = sheet.getRow(r);
                Map<String, Object> row = new LinkedHashMap<>();

                boolean allBlank = true;
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = rowObj != null
                        ? rowObj.getCell(c, MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        : null;
                    String value = cell != null ? formatter.formatCellValue(cell) : "";
                    if (!value.trim().isEmpty()) {
                        allBlank = false;
                    }
                    row.put(columns.get(c), value);
                }

                if (allBlank) {
                    continue;
                }

                totalRows++;
                if (preview.size() < previewSize) {
                    preview.add(row);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel解析失败");
        }

        return FilePreviewResult.builder()
            .columns(columns)
            .preview(preview)
            .totalRows(totalRows)
            .build();
    }

    private void forEachExcelDataRow(String filePath, DataRowConsumer consumer) {
        // 使用 DataFormatter 处理中文（设置 Locale 为 China）
        DataFormatter formatter = new DataFormatter(java.util.Locale.CHINA);

        try (InputStream in = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(in)) {

            if (workbook.getNumberOfSheets() <= DEFAULT_SHEET_INDEX) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel无有效工作表");
            }

            Sheet sheet = workbook.getSheetAt(DEFAULT_SHEET_INDEX);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            short lastCellNum = headerRow.getLastCellNum();
            if (lastCellNum <= 0) {
                throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel表头为空");
            }

            List<String> rawColumns = new ArrayList<>(lastCellNum);
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i, MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String col = formatter.formatCellValue(cell);
                rawColumns.add(col);
            }
            List<String> columns = makeUniqueColumns(normalizeColumns(rawColumns));

            int rowIndex = 0;
            int lastRowNum = sheet.getLastRowNum();
            for (int r = 1; r <= lastRowNum; r++) {
                Row rowObj = sheet.getRow(r);
                Map<String, Object> row = new LinkedHashMap<>();

                boolean allBlank = true;
                for (int c = 0; c < columns.size(); c++) {
                    Cell cell = rowObj != null
                        ? rowObj.getCell(c, MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        : null;
                    String value = cell != null ? formatter.formatCellValue(cell) : "";
                    if (!value.trim().isEmpty()) {
                        allBlank = false;
                    }
                    row.put(columns.get(c), value);
                }

                if (allBlank) {
                    continue;
                }

                rowIndex++;
                consumer.accept(rowIndex, row);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel解析失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.FILE_PARSE_FAILED, "Excel解析失败");
        }
    }

    private List<String> removeBomFromFirstField(List<String> fields) {
        if (fields.isEmpty()) {
            return fields;
        }
        String first = fields.get(0);
        if (first != null && !first.isEmpty() && first.charAt(0) == UTF8_BOM) {
            List<String> copy = new ArrayList<>(fields);
            copy.set(0, first.substring(1));
            return copy;
        }
        return fields;
    }

    private List<String> normalizeColumns(List<String> rawColumns) {
        List<String> result = new ArrayList<>(rawColumns.size());
        for (int i = 0; i < rawColumns.size(); i++) {
            String col = rawColumns.get(i);
            col = col != null ? col.trim() : "";
            if (col.isEmpty()) {
                col = "列" + (i + 1);
            }
            result.add(col);
        }
        return result;
    }

    private List<String> makeUniqueColumns(List<String> columns) {
        Map<String, Integer> counter = new HashMap<>();
        List<String> result = new ArrayList<>(columns.size());
        for (String c : columns) {
            String base = c != null ? c : "";
            if (!counter.containsKey(base)) {
                counter.put(base, 1);
                result.add(base);
                continue;
            }
            int idx = counter.get(base) + 1;
            counter.put(base, idx);
            result.add(base + "_" + idx);
        }
        return result;
    }

    /**
     * 扩展列名列表以适配数据行列数
     * 当数据行列数超过表头列数时，自动生成额外的列名
     */
    private List<String> expandColumns(List<String> columns, int targetSize) {
        if (targetSize <= columns.size()) {
            return columns;
        }
        List<String> expanded = new ArrayList<>(columns);
        for (int i = columns.size(); i < targetSize; i++) {
            expanded.add("列" + (i + 1));
        }
        log.info("自动扩展列名: 原有{}列, 扩展至{}列", columns.size(), targetSize);
        return makeUniqueColumns(expanded);
    }

    /**
     * 解析一行CSV（支持双引号与双引号转义）
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
                continue;
            }

            if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }

            if (ch == '"') {
                inQuotes = true;
                continue;
            }

            current.append(ch);
        }
        fields.add(current.toString());
        return fields;
    }
}
