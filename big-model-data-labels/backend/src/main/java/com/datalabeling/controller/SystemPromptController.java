package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.SystemPromptCreateRequest;
import com.datalabeling.dto.SystemPromptUpdateRequest;
import com.datalabeling.entity.SystemPrompt;
import com.datalabeling.service.SystemPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统提示词管理接口
 */
@Slf4j
@RestController
@RequestMapping("/system-prompts")
@RequiredArgsConstructor
public class SystemPromptController {

    private final SystemPromptService systemPromptService;

    /**
     * 查询用户可用的提示词列表（包含全局提示词）
     */
    @GetMapping
    public ApiResponse<List<SystemPrompt>> listPrompts(
            @RequestParam(required = false) String promptType,
            HttpServletRequest httpRequest) {
        Integer userId = getCurrentUserId(httpRequest);
        List<SystemPrompt> prompts = promptType != null
            ? systemPromptService.findAvailableForUserAndType(userId, promptType)
            : systemPromptService.findAvailableForUser(userId);
        return ApiResponse.success(prompts);
    }

    /**
     * 根据ID获取提示词详情
     */
    @GetMapping("/{id}")
    public ApiResponse<SystemPrompt> getPrompt(@PathVariable Integer id, HttpServletRequest httpRequest) {
        SystemPrompt prompt = systemPromptService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));
        return ApiResponse.success(prompt);
    }

    /**
     * 获取指定类型的默认提示词
     */
    @GetMapping("/default/{promptType}")
    public ApiResponse<SystemPrompt> getDefaultPrompt(@PathVariable String promptType, HttpServletRequest httpRequest) {
        SystemPrompt prompt = systemPromptService.findDefaultByType(promptType)
            .orElseThrow(() -> new IllegalArgumentException("没有找到默认提示词: " + promptType));
        return ApiResponse.success(prompt);
    }

    /**
     * 创建新提示词
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPrompt> createPrompt(
            @Valid @RequestBody SystemPromptCreateRequest request,
            HttpServletRequest httpRequest) {
        // 将 DTO 转换为实体
        SystemPrompt prompt = SystemPrompt.builder()
            .name(request.getName())
            .code(request.getCode())
            .promptType(request.getPromptType())
            .template(request.getTemplate())
            .variables(request.getVariables())
            .userId(getCurrentUserId(httpRequest))
            .isSystemDefault(request.getIsSystemDefault() != null ? request.getIsSystemDefault() : false)
            .isActive(request.getIsActive() != null ? request.getIsActive() : true)
            .build();

        SystemPrompt created = systemPromptService.create(prompt);
        return ApiResponse.success("提示词创建成功", created);
    }

    /**
     * 更新提示词
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPrompt> updatePrompt(
            @PathVariable Integer id,
            @Valid @RequestBody SystemPromptUpdateRequest request,
            HttpServletRequest httpRequest) {
        // 将 DTO 转换为实体
        SystemPrompt prompt = SystemPrompt.builder()
            .name(request.getName())
            .code(request.getCode())
            .promptType(request.getPromptType())
            .template(request.getTemplate())
            .variables(request.getVariables())
            .isSystemDefault(request.getIsSystemDefault())
            .isActive(request.getIsActive())
            .build();

        SystemPrompt updated = systemPromptService.update(id, prompt);
        return ApiResponse.success("提示词更新成功", updated);
    }

    /**
     * 删除提示词
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deletePrompt(@PathVariable Integer id, HttpServletRequest httpRequest) {
        systemPromptService.delete(id);
        return ApiResponse.success("提示词删除成功", null);
    }

    /**
     * 启用/禁用提示词
     */
    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPrompt> togglePrompt(@PathVariable Integer id, HttpServletRequest httpRequest) {
        SystemPrompt prompt = systemPromptService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));
        prompt.setIsActive(!prompt.getIsActive());
        SystemPrompt updated = systemPromptService.update(id, prompt);

        String action = updated.getIsActive() ? "启用" : "禁用";
        return ApiResponse.success("提示词已" + action, updated);
    }

    /**
     * 设置为系统默认提示词
     */
    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPrompt> setAsDefault(@PathVariable Integer id, HttpServletRequest httpRequest) {
        // 先取消该类型的其他默认
        SystemPrompt targetPrompt = systemPromptService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));

        // 校验提示词类型不能为空
        if (targetPrompt.getPromptType() == null) {
            throw new IllegalArgumentException("提示词类型不能为空，请先设置 promptType 字段");
        }

        List<SystemPrompt> existingDefaults = systemPromptService.findByType(targetPrompt.getPromptType());
        for (SystemPrompt p : existingDefaults) {
            // 防御性编程：跳过 null 元素
            if (p == null) {
                continue;
            }
            if (Boolean.TRUE.equals(p.getIsSystemDefault())) {
                p.setIsSystemDefault(false);
                systemPromptService.update(p.getId(), p);
            }
        }

        // 设置新的默认
        targetPrompt.setIsSystemDefault(true);
        SystemPrompt updated = systemPromptService.update(id, targetPrompt);

        return ApiResponse.success("已设置为默认提示词", updated);
    }

    // ==================== 缓存管理接口 ====================

    /**
     * 刷新指定提示词的缓存
     */
    @PostMapping("/{id}/refresh-cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemPrompt> refreshCache(@PathVariable Integer id) {
        SystemPrompt refreshed = systemPromptService.refreshCache(id);
        return ApiResponse.success("提示词缓存已刷新", refreshed);
    }

    /**
     * 批量刷新所有默认提示词缓存
     * 用于管理员修改模板后批量刷新，确保所有正在运行的任务使用最新模板
     */
    @PostMapping("/refresh-all-defaults")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> refreshAllDefaults() {
        systemPromptService.refreshAllDefaultPrompts();

        Map<String, String> result = new HashMap<>();
        result.put("message", "所有默认提示词缓存已刷新");
        result.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return ApiResponse.success("批量刷新完成", result);
    }

    /**
     * 清除所有提示词缓存（谨慎使用）
     */
    @PostMapping("/clear-cache")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, String>> clearAllCache() {
        systemPromptService.evictAll();

        Map<String, String> result = new HashMap<>();
        result.put("message", "所有提示词缓存已清除");
        result.put("timestamp", String.valueOf(System.currentTimeMillis()));

        return ApiResponse.success("缓存清除完成", result);
    }

    /**
     * 获取当前用户ID
     */
    private Integer getCurrentUserId(HttpServletRequest request) {
        // 从请求属性中获取用户ID（由过滤器/拦截器设置）
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            return (Integer) userIdAttr;
        }
        // 临时返回默认值，实际应从 SecurityContext 获取
        return 1;
    }
}
