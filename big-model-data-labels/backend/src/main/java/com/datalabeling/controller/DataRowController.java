package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.BatchUpdateDataRowsRequest;
import com.datalabeling.dto.request.UpdateDataRowRequest;
import com.datalabeling.dto.response.DataRowVO;
import com.datalabeling.service.DataRowService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 数据行控制器
 */
@RestController
@RequestMapping("/tasks/{taskId}/rows")
@RequiredArgsConstructor
@Validated
public class DataRowController {

    private final DataRowService dataRowService;

    /**
     * 获取任务数据行（分页）
     */
    @GetMapping
    public ApiResponse<PageResult<DataRowVO>> list(
        @PathVariable("taskId") Integer taskId,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "50") Integer size,
        HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(dataRowService.getRows(taskId, page, size, httpRequest));
    }

    /**
     * 批量更新数据行标签结果（仅未归档允许）
     */
    @PutMapping
    public ApiResponse<Void> batchUpdate(
        @PathVariable("taskId") Integer taskId,
        @Validated @RequestBody BatchUpdateDataRowsRequest request,
        HttpServletRequest httpRequest
    ) {
        dataRowService.updateRowsBatch(taskId, request, httpRequest);
        return ApiResponse.success("保存成功", null);
    }

    /**
     * 更新数据行标签结果（仅未归档允许）
     */
    @PutMapping("/{rowId}")
    public ApiResponse<Void> update(
        @PathVariable("taskId") Integer taskId,
        @PathVariable("rowId") Long rowId,
        @Validated @RequestBody UpdateDataRowRequest request,
        HttpServletRequest httpRequest
    ) {
        dataRowService.updateRow(taskId, rowId, request, httpRequest);
        return ApiResponse.success("保存成功", null);
    }
}

