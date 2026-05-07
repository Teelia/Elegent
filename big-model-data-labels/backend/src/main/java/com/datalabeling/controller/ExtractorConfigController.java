package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.dto.request.AiGenerateExtractorRequest;
import com.datalabeling.dto.request.CreateExtractorRequest;
import com.datalabeling.dto.request.UpdateExtractorRequest;
import com.datalabeling.dto.response.AiGenerateExtractorResponse;
import com.datalabeling.dto.response.ExtractorConfigVO;
import com.datalabeling.service.ExtractorAiService;
import com.datalabeling.service.ExtractorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提取器配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/extractors")
@RequiredArgsConstructor
public class ExtractorConfigController {

    private final ExtractorConfigService extractorConfigService;
    private final ExtractorAiService extractorAiService;

    /**
     * 获取所有激活的提取器
     */
    @GetMapping
    public ApiResponse<List<ExtractorConfigVO>> listAll() {
        List<ExtractorConfigVO> list = extractorConfigService.listAllActive();
        return ApiResponse.success(list);
    }

    /**
     * 获取所有内置提取器
     */
    @GetMapping("/builtin")
    public ApiResponse<List<ExtractorConfigVO>> listBuiltin() {
        List<ExtractorConfigVO> list = extractorConfigService.listBuiltin();
        return ApiResponse.success(list);
    }

    /**
     * 获取所有自定义提取器
     */
    @GetMapping("/custom")
    public ApiResponse<List<ExtractorConfigVO>> listCustom() {
        List<ExtractorConfigVO> list = extractorConfigService.listCustom();
        return ApiResponse.success(list);
    }

    /**
     * 根据ID获取提取器详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ExtractorConfigVO> getById(@PathVariable Integer id) {
        ExtractorConfigVO vo = extractorConfigService.getById(id);
        return ApiResponse.success(vo);
    }

    /**
     * 根据代码获取提取器详情
     */
    @GetMapping("/code/{code}")
    public ApiResponse<ExtractorConfigVO> getByCode(@PathVariable String code) {
        ExtractorConfigVO vo = extractorConfigService.getByCode(code);
        return ApiResponse.success(vo);
    }

    /**
     * 创建提取器
     */
    @PostMapping
    public ApiResponse<ExtractorConfigVO> create(@RequestBody CreateExtractorRequest request) {
        Integer userId = getCurrentUserId();
        ExtractorConfigVO vo = extractorConfigService.create(userId, request);
        return ApiResponse.success(vo);
    }

    /**
     * 更新提取器
     */
    @PutMapping("/{id}")
    public ApiResponse<ExtractorConfigVO> update(
            @PathVariable Integer id,
            @RequestBody UpdateExtractorRequest request) {
        ExtractorConfigVO vo = extractorConfigService.update(id, request);
        return ApiResponse.success(vo);
    }

    /**
     * 删除提取器
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        extractorConfigService.delete(id);
        return ApiResponse.success(null);
    }

    /**
     * AI辅助生成提取器
     */
    @PostMapping("/ai-generate")
    public ApiResponse<AiGenerateExtractorResponse> aiGenerate(
            @RequestBody AiGenerateExtractorRequest request) {
        log.info("AI生成提取器请求: mode={}, name={}", request.getMode(), request.getExtractorName());
        AiGenerateExtractorResponse response = extractorAiService.generateExtractor(request);
        return ApiResponse.success(response);
    }

    /**
     * 获取当前用户ID
     */
    private Integer getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            // 从用户名获取用户ID（这里简化处理，实际应该从UserDetails中获取）
            return 1; // 默认返回1，实际应该从认证信息中获取
        }
        return 1;
    }
}