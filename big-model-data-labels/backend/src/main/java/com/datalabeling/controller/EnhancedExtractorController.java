package com.datalabeling.controller;

import com.datalabeling.common.Result;
import com.datalabeling.service.extraction.EnhancedExtractionOrchestrator;
import com.datalabeling.service.extraction.EnhancedExtractedResult;
import com.datalabeling.service.extraction.ExtractorMetadata;
import com.datalabeling.service.extraction.ExtractorRegistry;
import com.datalabeling.service.extraction.IEnhancedExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强型提取器控制器
 * 提供提取器查询、测试、元数据获取等API
 */
@Slf4j
@RestController
@RequestMapping("/api/enhanced-extractors")
@RequiredArgsConstructor
public class EnhancedExtractorController {

    private final EnhancedExtractionOrchestrator orchestrator;
    private final ExtractorRegistry registry;

    /**
     * 获取所有可用的提取器
     */
    @GetMapping("/list")
    public Result<List<ExtractorMetadata>> listExtractors() {
        try {
            List<ExtractorMetadata> extractors = orchestrator.getAvailableExtractors();
            log.info("获取提取器列表，共 {} 个", extractors.size());
            return Result.success(extractors);
        } catch (Exception e) {
            log.error("获取提取器列表失败", e);
            return Result.error(500, "获取提取器列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取内置提取器
     */
    @GetMapping("/builtin")
    public Result<List<ExtractorMetadata>> getBuiltinExtractors() {
        try {
            List<ExtractorMetadata> extractors = registry.getBuiltinExtractors().stream()
                .map(IEnhancedExtractor::getMetadata)
                .collect(Collectors.toList());
            return Result.success(extractors);
        } catch (Exception e) {
            log.error("获取内置提取器失败", e);
            return Result.error(500, "获取内置提取器失败: " + e.getMessage());
        }
    }

    /**
     * 根据分类获取提取器
     */
    @GetMapping("/category/{category}")
    public Result<List<ExtractorMetadata>> getExtractorsByCategory(@PathVariable String category) {
        try {
            List<ExtractorMetadata> extractors = registry.getByCategory(category).stream()
                .map(IEnhancedExtractor::getMetadata)
                .collect(Collectors.toList());
            return Result.success(extractors);
        } catch (Exception e) {
            log.error("按分类获取提取器失败", e);
            return Result.error(500, "按分类获取提取器失败: " + e.getMessage());
        }
    }

    /**
     * 根据标签获取提取器
     */
    @GetMapping("/tag/{tag}")
    public Result<List<ExtractorMetadata>> getExtractorsByTag(@PathVariable String tag) {
        try {
            List<ExtractorMetadata> extractors = registry.getByTag(tag).stream()
                .map(IEnhancedExtractor::getMetadata)
                .collect(Collectors.toList());
            return Result.success(extractors);
        } catch (Exception e) {
            log.error("按标签获取提取器失败", e);
            return Result.error(500, "按标签获取提取器失败: " + e.getMessage());
        }
    }

    /**
     * 搜索提取器
     */
    @GetMapping("/search")
    public Result<List<ExtractorMetadata>> searchExtractors(@RequestParam String keyword) {
        try {
            List<ExtractorMetadata> extractors = registry.search(keyword).stream()
                .map(IEnhancedExtractor::getMetadata)
                .collect(Collectors.toList());
            return Result.success(extractors);
        } catch (Exception e) {
            log.error("搜索提取器失败", e);
            return Result.error(500, "搜索提取器失败: " + e.getMessage());
        }
    }

    /**
     * 获取提取器详细信息
     */
    @GetMapping("/{code}")
    public Result<Map<String, Object>> getExtractorDetail(@PathVariable String code) {
        try {
            IEnhancedExtractor extractor = registry.getByCodeWithAlias(code);
            if (extractor == null) {
                return Result.error(404, "提取器不存在: " + code);
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("metadata", extractor.getMetadata());
            detail.put("defaultOptions", extractor.getDefaultOptions());
            detail.put("examples", extractor.getExamples());

            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取提取器详情失败", e);
            return Result.error(500, "获取提取器详情失败: " + e.getMessage());
        }
    }

    /**
     * 测试提取器
     */
    @PostMapping("/{code}/test")
    public Result<Map<String, Object>> testExtractor(
        @PathVariable String code,
        @RequestBody Map<String, Object> request) {
        try {
            IEnhancedExtractor extractor = registry.getByCodeWithAlias(code);
            if (extractor == null) {
                return Result.error(404, "提取器不存在: " + code);
            }

            String text = (String) request.get("text");
            @SuppressWarnings("unchecked")
            Map<String, Object> options = (Map<String, Object>) request.getOrDefault("options", new HashMap<>());

            // 合并默认选项
            Map<String, Object> defaultOptions = extractor.getDefaultOptions();
            if (defaultOptions != null) {
                Map<String, Object> mergedOptions = new HashMap<>(defaultOptions);
                mergedOptions.putAll(options);
                options = mergedOptions;
            }

            // 执行提取
            List<EnhancedExtractedResult> results = extractor.extractWithContext(text, options);

            // 构建返回结果
            Map<String, Object> response = new HashMap<>();
            response.put("extractor", extractor.getMetadata());
            response.put("results", results);
            response.put("count", results.size());
            response.put("llmPromptContext", extractor.buildLLMPromptContext(results));

            return Result.success(response);
        } catch (Exception e) {
            log.error("测试提取器失败", e);
            return Result.error(500, "测试提取器失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有分类
     */
    @GetMapping("/categories")
    public Result<Map<String, Integer>> getCategories() {
        try {
            Map<String, Integer> stats = registry.getCategoryStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取分类统计失败", e);
            return Result.error(500, "获取分类统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    public Result<Map<String, Integer>> getTags() {
        try {
            Map<String, Integer> stats = registry.getTagStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取标签统计失败", e);
            return Result.error(500, "获取标签统计失败: " + e.getMessage());
        }
    }

    /**
     * 刷新提取器注册
     */
    @PostMapping("/refresh")
    public Result<String> refreshRegistry() {
        try {
            registry.refresh();
            log.info("刷新提取器注册成功");
            return Result.success("刷新成功");
        } catch (Exception e) {
            log.error("刷新提取器注册失败", e);
            return Result.error(500, "刷新失败: " + e.getMessage());
        }
    }

    /**
     * 获取注册中心统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalExtractors", registry.getExtractorCount());
            stats.put("categories", registry.getCategoryStatistics());
            stats.put("tags", registry.getTagStatistics());
            stats.put("codes", registry.getCodes());
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.error(500, "获取统计信息失败: " + e.getMessage());
        }
    }
}
