package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.CreateAnalysisTaskRequest;
import com.datalabeling.dto.response.AnalysisTaskVO;
import com.datalabeling.dto.response.AnalysisProcessVO;
import com.datalabeling.dto.response.AnalysisTaskProgressVO;
import com.datalabeling.entity.TaskExecutionLog;
import com.datalabeling.service.AnalysisTaskNewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分析任务控制器（新版）
 */
@Slf4j
@RestController
@RequestMapping("/analysis-tasks")
@RequiredArgsConstructor
public class AnalysisTaskNewController {
    
    private final AnalysisTaskNewService analysisTaskService;
    
    /**
     * 获取分析任务列表
     * 如果指定datasetId，则返回该数据集的任务；否则返回当前用户的所有任务
     */
    @GetMapping
    public ApiResponse<PageResult<AnalysisTaskVO>> getTasks(
            @RequestParam(required = false) Integer datasetId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.success(analysisTaskService.getTasks(datasetId, status, page, size));
    }
    
    /**
     * 获取分析任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<AnalysisTaskVO> getTask(@PathVariable Integer id) {
        return ApiResponse.success(analysisTaskService.getTask(id));
    }

    /**
     * 获取任务进度（用于前端轮询）
     */
    @GetMapping("/{id}/progress")
    public ApiResponse<AnalysisTaskProgressVO> getTaskProgress(@PathVariable Integer id) {
        return ApiResponse.success(analysisTaskService.getTaskProgress(id));
    }

    /**
     * 创建分析任务
     */
    @PostMapping
    public ApiResponse<AnalysisTaskVO> createTask(@Valid @RequestBody CreateAnalysisTaskRequest request) {
        return ApiResponse.success(analysisTaskService.createTask(request));
    }
    
    /**
     * 启动分析任务
     */
    @PostMapping("/{id}/start")
    public ApiResponse<AnalysisTaskVO> startTask(@PathVariable Integer id) {
        return ApiResponse.success(analysisTaskService.startTask(id));
    }
    
    /**
     * 暂停分析任务
     */
    @PostMapping("/{id}/pause")
    public ApiResponse<AnalysisTaskVO> pauseTask(@PathVariable Integer id) {
        return ApiResponse.success(analysisTaskService.pauseTask(id));
    }
    
    /**
     * 恢复分析任务（从暂停状态继续执行）
     */
    @PostMapping("/{id}/resume")
    public ApiResponse<AnalysisTaskVO> resumeTask(@PathVariable Integer id) {
        // 复用 startTask 逻辑，因为 startTask 已支持从 PAUSED 状态启动
        return ApiResponse.success(analysisTaskService.startTask(id));
    }
    
    /**
     * 取消分析任务
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Integer id) {
        analysisTaskService.cancelTask(id);
        return ApiResponse.success(null);
    }
    
    /**
     * 删除分析任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Integer id) {
        analysisTaskService.deleteTask(id);
        return ApiResponse.success(null);
    }
    
    /**
     * 获取任务执行日志
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<PageResult<TaskExecutionLog>> getTaskLogs(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestParam(required = false) String level) {
        return ApiResponse.success(analysisTaskService.getTaskLogs(id, page, size, level));
    }
    
    /**
     * 获取增量日志（用于实时更新）
     * @param id 任务ID
     * @param since 从该时间之后的日志
     * @param limit 最大返回条数
     */
    @GetMapping("/{id}/logs/since")
    public ApiResponse<List<TaskExecutionLog>> getLogsSince(
            @PathVariable Integer id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "100") Integer limit) {
        return ApiResponse.success(analysisTaskService.getLogsSince(id, since, limit));
    }
    
    /**
     * 获取分析过程详情（包含标签列表和最新日志）
     * 用于前端定时轮询展示分析进度
     */
    @GetMapping("/{id}/process")
    public ApiResponse<AnalysisProcessVO> getAnalysisProcess(@PathVariable Integer id) {
        return ApiResponse.success(analysisTaskService.getAnalysisProcess(id));
    }

    /**
     * 导出分析任务结果为Excel
     * @param id 任务ID
     * @param includeReasoning 是否包含判断依据（合并到结果单元格中）
     */
    @GetMapping("/{id}/export")
    public void exportTask(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "false") Boolean includeReasoning,
            HttpServletResponse response) throws IOException {
        Map<String, Object> exportData = analysisTaskService.getExportData(id, includeReasoning);

        String taskName = (String) exportData.get("taskName");
        List<String> originalColumns = (List<String>) exportData.get("originalColumns");
        List<String> labelColumns = (List<String>) exportData.get("labelColumns");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) exportData.get("rows");

        // 设置响应头
        String filename = URLEncoder.encode(taskName + "_分析结果.xlsx", StandardCharsets.UTF_8.name())
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("分析结果");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 创建标签结果列样式（黄色背景）
            CellStyle labelHeaderStyle = workbook.createCellStyle();
            labelHeaderStyle.setFont(headerFont);
            labelHeaderStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            labelHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写入表头
            Row headerRow = sheet.createRow(0);
            int colIndex = 0;

            // 行号列
            Cell rowNumCell = headerRow.createCell(colIndex++);
            rowNumCell.setCellValue("行号");
            rowNumCell.setCellStyle(headerStyle);

            // 原始数据列
            for (String col : originalColumns) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellValue(col);
                cell.setCellStyle(headerStyle);
            }

            // 标签结果列
            for (String labelCol : labelColumns) {
                Cell cell = headerRow.createCell(colIndex++);
                cell.setCellValue(labelCol);
                cell.setCellStyle(labelHeaderStyle);
            }

            // 写入数据行
            int rowNum = 1;
            for (Map<String, Object> rowData : rows) {
                Row row = sheet.createRow(rowNum++);
                colIndex = 0;

                // 行号
                Integer rowIndex = (Integer) rowData.get("rowIndex");
                row.createCell(colIndex++).setCellValue(rowIndex != null ? rowIndex : rowNum - 1);

                // 原始数据
                Map<String, Object> originalData = (Map<String, Object>) rowData.get("originalData");
                for (String col : originalColumns) {
                    Cell cell = row.createCell(colIndex++);
                    Object value = originalData != null ? originalData.get(col) : null;
                    if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }

                // 标签结果
                Map<String, String> labelResults = (Map<String, String>) rowData.get("labelResults");
                for (String labelCol : labelColumns) {
                    Cell cell = row.createCell(colIndex++);
                    String result = labelResults != null ? labelResults.get(labelCol) : null;
                    if (result != null) {
                        cell.setCellValue(result);
                    }
                }
            }

            // 自动调整列宽（最多20列，防止大文件卡死）
            int totalCols = Math.min(1 + originalColumns.size() + labelColumns.size(), 20);
            for (int i = 0; i < totalCols; i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入响应
            workbook.write(response.getOutputStream());
        }

        log.info("导出任务 {} 完成, 共 {} 行", id, rows.size());
    }
}