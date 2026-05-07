package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.request.CreateModelConfigRequest;
import com.datalabeling.dto.request.UpdateModelConfigRequest;
import com.datalabeling.dto.response.ModelConfigVO;
import com.datalabeling.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 管理员大模型配置控制器
 */
@RestController
@RequestMapping("/admin/model-configs")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminModelConfigController {

    private final ModelConfigService modelConfigService;

    /**
     * 获取所有模型配置列表
     */
    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list() {
        return ApiResponse.success(modelConfigService.listAllConfigs());
    }

    /**
     * 获取激活的模型配置列表
     */
    @GetMapping("/active")
    public ApiResponse<List<ModelConfigVO>> listActive() {
        return ApiResponse.success(modelConfigService.listActiveConfigs());
    }

    /**
     * 获取默认配置
     */
    @GetMapping("/default")
    public ApiResponse<ModelConfigVO> getDefault() {
        return ApiResponse.success(modelConfigService.getDefaultConfig());
    }

    /**
     * 根据ID获取配置
     */
    @GetMapping("/{id}")
    public ApiResponse<ModelConfigVO> getById(@PathVariable Integer id) {
        return ApiResponse.success(modelConfigService.getConfigById(id));
    }

    /**
     * 创建新配置
     */
    @PostMapping
    public ApiResponse<ModelConfigVO> create(@Validated @RequestBody CreateModelConfigRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.success("创建成功", modelConfigService.createConfig(request, httpRequest));
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(@PathVariable Integer id,
                                             @Validated @RequestBody UpdateModelConfigRequest request,
                                             HttpServletRequest httpRequest) {
        return ApiResponse.success("更新成功", modelConfigService.updateConfig(id, request, httpRequest));
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id, HttpServletRequest httpRequest) {
        modelConfigService.deleteConfig(id, httpRequest);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 设置默认配置
     */
    @PostMapping("/{id}/set-default")
    public ApiResponse<ModelConfigVO> setDefault(@PathVariable Integer id, HttpServletRequest httpRequest) {
        return ApiResponse.success("设置成功", modelConfigService.setDefaultConfig(id, httpRequest));
    }

    // ========== 兼容旧接口 ==========

    /**
     * 获取DeepSeek配置（兼容旧接口）
     */
    @GetMapping("/deepseek")
    public ApiResponse<ModelConfigVO> getDeepSeek() {
        return ApiResponse.success(modelConfigService.getDeepSeekConfig());
    }

    /**
     * 更新DeepSeek配置（兼容旧接口）
     */
    @PutMapping("/deepseek")
    public ApiResponse<ModelConfigVO> updateDeepSeek(@Validated @RequestBody UpdateModelConfigRequest request,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.success("保存成功", modelConfigService.updateDeepSeekConfig(request, httpRequest));
    }
}

