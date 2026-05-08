package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.CreateLabelRequest;
import com.datalabeling.dto.request.UpdateLabelRequest;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.service.AuditService;
import com.datalabeling.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;
    private final AuditService auditService;

    /**
     * 创建标签
     */
    @PostMapping
    public ApiResponse<LabelVO> createLabel(@Validated @RequestBody CreateLabelRequest request, HttpServletRequest httpRequest) {
        LabelVO labelVO = labelService.createLabel(request);
        HashMap<String, Object> details = new HashMap<>();
        details.put("name", labelVO.getName());
        details.put("version", labelVO.getVersion());
        auditService.record("create_label", "label", labelVO.getId(), details, httpRequest);
        return ApiResponse.success("标签创建成功", labelVO);
    }

    /**
     * 获取标签列表（分页）
     */
    @GetMapping
    public ApiResponse<PageResult<LabelVO>> getLabelList(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) Integer userId,
        HttpServletRequest httpRequest
    ) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResult<LabelVO> result = labelService.getLabelList(pageable, userId, httpRequest);
        return ApiResponse.success(result);
    }

    /**
     * 获取所有激活的标签（最新版本）
     * @param userId 用户ID（管理员可指定）
     * @param scope 作用域过滤：global（系统内置全局，所有用户可见）, dataset（数据集专属）
     * @param datasetId 数据集ID（scope=dataset时必须）
     */
    @GetMapping("/active")
    public ApiResponse<List<LabelVO>> getActiveLabels(
        @RequestParam(required = false) Integer userId,
        @RequestParam(required = false) String scope,
        @RequestParam(required = false) Integer datasetId,
        HttpServletRequest httpRequest
    ) {
        List<LabelVO> labels = labelService.getActiveLabels(userId, scope, datasetId, httpRequest);
        return ApiResponse.success(labels);
    }

    /**
     * 获取标签详情
     */
    @GetMapping("/{id}")
    public ApiResponse<LabelVO> getLabelById(@PathVariable Integer id, HttpServletRequest httpRequest) {
        LabelVO labelVO = labelService.getLabelById(id, httpRequest);
        return ApiResponse.success(labelVO);
    }

    /**
     * 获取标签版本历史
     */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<LabelVO>> getVersions(@PathVariable Integer id, HttpServletRequest httpRequest) {
        return ApiResponse.success(labelService.getLabelVersions(id, httpRequest));
    }

    /**
     * 更新标签（创建新版本）
     */
    @PutMapping("/{id}")
    public ApiResponse<LabelVO> updateLabel(
        @PathVariable Integer id,
        @Validated @RequestBody UpdateLabelRequest request,
        HttpServletRequest httpRequest
    ) {
        LabelVO labelVO = labelService.updateLabel(id, request);
        HashMap<String, Object> details = new HashMap<>();
        details.put("name", labelVO.getName());
        details.put("version", labelVO.getVersion());
        details.put("fromId", id);
        auditService.record("update_label", "label", labelVO.getId(), details, httpRequest);
        return ApiResponse.success("标签更新成功（已创建新版本）", labelVO);
    }

    /**
     * 删除标签
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteLabel(@PathVariable Integer id, HttpServletRequest httpRequest) {
        LabelVO deleted = labelService.deleteLabel(id);
        HashMap<String, Object> details = new HashMap<>();
        details.put("name", deleted.getName());
        details.put("version", deleted.getVersion());
        auditService.record("delete_label", "label", id, details, httpRequest);
        return ApiResponse.success("标签删除成功", null);
    }
}
