package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.service.BuiltinLabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 内置全局标签接口
 */
@Slf4j
@RestController
@RequestMapping("/builtin-labels")
@RequiredArgsConstructor
public class BuiltinLabelController {

    private final BuiltinLabelService builtinLabelService;

    /**
     * 获取内置全局标签列表（分页）
     */
    @GetMapping
    public ApiResponse<PageResult<LabelVO>> list(
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        HttpServletRequest request
    ) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return ApiResponse.success(builtinLabelService.list(category, pageable, request));
    }

    /**
     * 获取内置标签分类
     */
    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories(HttpServletRequest request) {
        return ApiResponse.success(builtinLabelService.listCategories(request));
    }

    /**
     * 启用/禁用内置标签（仅管理员）
     */
    @PutMapping("/{id}/active")
    public ApiResponse<Void> setActive(
        @PathVariable Integer id,
        @RequestParam boolean active,
        HttpServletRequest request
    ) {
        builtinLabelService.setActive(id, active, request);
        return ApiResponse.success(null);
    }
}

