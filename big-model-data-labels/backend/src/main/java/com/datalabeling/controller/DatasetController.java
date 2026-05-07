package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.CreateDatasetRequest;
import com.datalabeling.dto.response.DatasetVO;
import com.datalabeling.entity.DataRow;
import com.datalabeling.service.DataExportService;
import com.datalabeling.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;

/**
 * 数据集控制器
 */
@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final DataExportService dataExportService;
    
    /**
     * 获取数据集列表
     */
    @GetMapping
    public ApiResponse<PageResult<DatasetVO>> getDatasets(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ApiResponse.success(datasetService.getDatasets(page, size, status, search));
    }
    
    /**
     * 获取数据集详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DatasetVO> getDataset(@PathVariable Integer id) {
        return ApiResponse.success(datasetService.getDataset(id));
    }

    /**
     * 查询数据集导入进度
     *
     * @param id 数据集ID
     * @return 导入进度信息
     */
    @GetMapping("/{id}/import-progress")
    public ApiResponse<ImportProgressVO> getImportProgress(@PathVariable Integer id) {
        return ApiResponse.success(datasetService.getImportProgress(id));
    }

    /**
     * 导入进度信息
     */
    public static class ImportProgressVO {
        private Integer datasetId;
        private String status;        // importing, uploaded, failed
        private Integer totalRows;    // 已导入行数
        private String message;       // 状态消息

        public ImportProgressVO() {}

        public ImportProgressVO(Integer datasetId, String status, Integer totalRows, String message) {
            this.datasetId = datasetId;
            this.status = status;
            this.totalRows = totalRows;
            this.message = message;
        }

        public Integer getDatasetId() {
            return datasetId;
        }

        public void setDatasetId(Integer datasetId) {
            this.datasetId = datasetId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getTotalRows() {
            return totalRows;
        }

        public void setTotalRows(Integer totalRows) {
            this.totalRows = totalRows;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
    
    /**
     * 上传数据集
     * 文件内容直接解析存入数据库，不保存原始文件
     */
    @PostMapping("/upload")
    public ApiResponse<DatasetVO> uploadDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description) {
        return ApiResponse.success(datasetService.uploadDataset(file, name, description));
    }
    
    /**
     * 更新数据集
     */
    @PutMapping("/{id}")
    public ApiResponse<DatasetVO> updateDataset(
            @PathVariable Integer id,
            @Valid @RequestBody CreateDatasetRequest request) {
        return ApiResponse.success(datasetService.updateDataset(id, request.getDescription()));
    }
    
    /**
     * 删除数据集
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDataset(@PathVariable Integer id) {
        datasetService.deleteDataset(id);
        return ApiResponse.success(null);
    }
    
    /**
     * 归档数据集
     */
    @PostMapping("/{id}/archive")
    public ApiResponse<DatasetVO> archiveDataset(@PathVariable Integer id) {
        return ApiResponse.success(datasetService.archiveDataset(id));
    }
    
    /**
     * 获取数据集的数据行
     *
     * @param id      数据集ID
     * @param page    页码（默认1）
     * @param size    每页大小（默认50）
     * @param keyword 搜索关键词（可选，在所有列中模糊搜索）
     * @return 数据行分页结果
     */
    @GetMapping("/{id}/rows")
    public ApiResponse<PageResult<DataRow>> getDataRows(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(datasetService.getDataRows(id, page, size, keyword));
    }

    /**
     * 批量更新数据行
     *
     * @param id      数据集ID
     * @param updates 更新列表
     * @return 成功响应
     */
    @PutMapping("/{id}/rows/batch")
    public ApiResponse<Void> batchUpdateDataRows(
            @PathVariable Integer id,
            @RequestBody java.util.List<DataRowUpdateRequest> updates) {
        datasetService.batchUpdateDataRows(id, updates);
        return ApiResponse.success(null);
    }

    /**
     * 数据行更新请求
     */
    public static class DataRowUpdateRequest {
        private Long rowId;
        private java.util.Map<String, Object> originalData;

        public Long getRowId() {
            return rowId;
        }

        public void setRowId(Long rowId) {
            this.rowId = rowId;
        }

        public java.util.Map<String, Object> getOriginalData() {
            return originalData;
        }

        public void setOriginalData(java.util.Map<String, Object> originalData) {
            this.originalData = originalData;
        }
    }

    /**
     * 导出数据集为 Excel 格式
     */
    @GetMapping("/{id}/export/excel")
    public void exportToExcel(@PathVariable Integer id, HttpServletResponse response) throws IOException {
        dataExportService.exportToExcel(id, response);
    }

    /**
     * 导出数据集为 CSV 格式
     */
    @GetMapping("/{id}/export/csv")
    public void exportToCsv(@PathVariable Integer id, HttpServletResponse response) throws IOException {
        dataExportService.exportToCsv(id, response);
    }
}