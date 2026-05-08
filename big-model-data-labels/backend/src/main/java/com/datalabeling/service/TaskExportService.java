package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.entity.DataRow;
import com.datalabeling.entity.FileTask;
import com.datalabeling.entity.TaskLabel;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.DataRowRepository;
import com.datalabeling.repository.FileTaskRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 任务导出服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExportService {

    private final FileTaskRepository fileTaskRepository;
    private final DataRowRepository dataRowRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final SecurityUtil securityUtil;

    public ExportDownload exportDownload(Integer taskId) {
        FileTask task = loadTaskAndCheckPermission(taskId);

        List<TaskLabel> taskLabels = taskLabelRepository.findByTaskIdOrderByIdAsc(taskId);
        List<String> labelKeys = new ArrayList<>();
        List<String> labelHeaders = new ArrayList<>();
        for (TaskLabel tl : taskLabels) {
            labelKeys.add(buildLabelKey(tl.getLabelName(), tl.getLabelVersion()));
            labelHeaders.add(tl.getLabelName());
        }

        Optional<DataRow> firstRow = dataRowRepository.findFirstByTaskIdOrderByRowIndexAsc(taskId);
        List<String> columns = firstRow.isPresent() && firstRow.get().getOriginalData() != null
            ? new ArrayList<>(firstRow.get().getOriginalData().keySet())
            : new ArrayList<>();

        StreamingResponseBody body = outputStream -> {
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
                Sheet sheet = workbook.createSheet("结果");

                int rowNum = 0;
                Row header = sheet.createRow(rowNum++);
                int colNum = 0;
                for (String c : columns) {
                    Cell cell = header.createCell(colNum++);
                    cell.setCellValue(c);
                }
                for (String c : labelHeaders) {
                    Cell cell = header.createCell(colNum++);
                    cell.setCellValue(c);
                }

                int page = 0;
                int size = 500;
                while (true) {
                    Page<DataRow> dataPage = dataRowRepository.findByTaskId(
                        taskId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "rowIndex"))
                    );

                    for (DataRow dr : dataPage.getContent()) {
                        Row row = sheet.createRow(rowNum++);
                        writeRow(row, columns, labelKeys, dr);
                    }

                    if (!dataPage.hasNext()) {
                        break;
                    }
                    page++;
                }

                workbook.write(outputStream);
                outputStream.flush();
                workbook.dispose();
            } catch (Exception e) {
                log.error("导出失败: taskId={}, {}", taskId, e.getMessage(), e);
                throw new BusinessException(ErrorCode.EXPORT_FAILED, "导出失败");
            }
        };
        return new ExportDownload(buildExportFilename(task), body);
    }

    public String buildExportFilename(FileTask task) {
        String base = task.getOriginalFilename() != null ? task.getOriginalFilename() : ("task-" + task.getId());
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = base.endsWith(".xlsx") ? base : (base + "-labeled.xlsx");
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileName;
        }
    }

    private FileTask loadTaskAndCheckPermission(Integer taskId) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        FileTask task = fileTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!securityUtil.hasPermission(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return task;
    }

    public static class ExportDownload {
        private final String fileName;
        private final StreamingResponseBody body;

        public ExportDownload(String fileName, StreamingResponseBody body) {
            this.fileName = fileName;
            this.body = body;
        }

        public String getFileName() {
            return fileName;
        }

        public StreamingResponseBody getBody() {
            return body;
        }
    }

    private void writeRow(Row row, List<String> columns, List<String> labelKeys, DataRow dr) {
        int col = 0;
        Map<String, Object> original = dr.getOriginalData();
        Map<String, Object> labels = dr.getLabelResults();

        for (String c : columns) {
            String v = original != null && original.get(c) != null ? String.valueOf(original.get(c)) : "";
            row.createCell(col++).setCellValue(v);
        }
        for (String k : labelKeys) {
            String v = extractLabelResultForExport(labels, k);
            row.createCell(col++).setCellValue(v);
        }
    }

    /**
     * 从标签结果中提取用于导出的值
     * 支持分类结果（字符串）和提取结果（Map结构）
     */
    @SuppressWarnings("unchecked")
    private String extractLabelResultForExport(Map<String, Object> labels, String labelKey) {
        if (labels == null) {
            return "";
        }
        Object value = labels.get(labelKey);
        if (value == null) {
            return "";
        }

        // 如果是 Map 结构（提取类型或带推理的分类结果）
        if (value instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) value;
            // 优先取 result 字段
            Object result = resultMap.get("result");
            if (result != null) {
                return String.valueOf(result);
            }
            // 其次取 summary 字段（结构化提取的摘要）
            Object summary = resultMap.get("summary");
            if (summary != null) {
                return String.valueOf(summary);
            }
            // 最后尝试取 extractedData 并格式化
            Object extractedData = resultMap.get("extractedData");
            if (extractedData instanceof Map) {
                return formatExtractedData((Map<String, Object>) extractedData);
            }
            return "";
        }

        // 普通字符串结果（分类标签的"是/否"）
        return String.valueOf(value);
    }

    /**
     * 格式化结构化提取的数据为可读字符串
     */
    private String formatExtractedData(Map<String, Object> extractedData) {
        if (extractedData == null || extractedData.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private String buildLabelKey(String labelName, Integer version) {
        return labelName + "_v" + version;
    }
}
