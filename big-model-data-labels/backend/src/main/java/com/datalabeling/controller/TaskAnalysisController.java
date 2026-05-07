package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.response.KeywordCountVO;
import com.datalabeling.dto.response.TaskStatisticsVO;
import com.datalabeling.service.TaskAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务分析接口（统计/关键词）
 */
@RestController
@RequestMapping("/tasks/{taskId}")
@RequiredArgsConstructor
public class TaskAnalysisController {

    private final TaskAnalysisService taskAnalysisService;

    @GetMapping("/statistics")
    public ApiResponse<TaskStatisticsVO> statistics(@PathVariable("taskId") Integer taskId, HttpServletRequest httpRequest) {
        return ApiResponse.success(taskAnalysisService.getStatistics(taskId, httpRequest));
    }

    @GetMapping("/keywords")
    public ApiResponse<List<KeywordCountVO>> keywords(
        @PathVariable("taskId") Integer taskId,
        @RequestParam("labelKey") String labelKey,
        @RequestParam("columns") String columns,
        @RequestParam(value = "top", required = false) Integer top,
        HttpServletRequest httpRequest
    ) {
        List<String> cols = splitColumns(columns);
        return ApiResponse.success(taskAnalysisService.getKeywords(taskId, labelKey, cols, top, httpRequest));
    }

    private List<String> splitColumns(String columns) {
        List<String> result = new ArrayList<>();
        if (columns == null || columns.trim().isEmpty()) {
            return result;
        }
        for (String c : columns.split(",")) {
            String v = c != null ? c.trim() : "";
            if (!v.isEmpty()) {
                result.add(v);
            }
        }
        return result;
    }
}

