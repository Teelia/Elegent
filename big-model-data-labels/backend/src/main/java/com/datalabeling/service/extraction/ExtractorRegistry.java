package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 提取器注册中心
 * 负责管理所有预置提取器和自定义提取器
 *
 * 核心功能：
 * 1. 自动注册Spring容器中的IEnhancedExtractor实现
 * 2. 提取器查询和检索
 * 3. 提取器元数据聚合
 * 4. 与数据库配置的动态提取器集成
 */
@Slf4j
@Component
public class ExtractorRegistry {

    /**
     * 自动注入所有IEnhancedExtractor实现
     */
    @Autowired(required = false)
    private List<IEnhancedExtractor> enhancedExtractors = new ArrayList<>();

    /**
     * 代码 -> 提取器映射
     */
    private final Map<String, IEnhancedExtractor> extractorByCode = new LinkedHashMap<>();

    /**
     * 分类 -> 提取器列表映射
     */
    private final Map<String, List<IEnhancedExtractor>> extractorsByCategory = new LinkedHashMap<>();

    /**
     * 标签 -> 提取器列表映射
     */
    private final Map<String, List<IEnhancedExtractor>> extractorsByTag = new LinkedHashMap<>();

    /**
     * 初始化注册中心
     * 自动注册所有Spring容器中的提取器
     */
    @PostConstruct
    public void initialize() {
        log.info("正在初始化提取器注册中心...");

        // 清空现有注册
        extractorByCode.clear();
        extractorsByCategory.clear();
        extractorsByTag.clear();

        // 注册所有增强型提取器
        if (enhancedExtractors != null) {
            for (IEnhancedExtractor extractor : enhancedExtractors) {
                registerExtractor(extractor);
            }
        }

        log.info("提取器注册中心初始化完成，共注册 {} 个提取器", extractorByCode.size());
        logRegisteredExtractors();
    }

    /**
     * 注册单个提取器
     */
    public void registerExtractor(IEnhancedExtractor extractor) {
        ExtractorMetadata metadata = extractor.getMetadata();
        String code = metadata.getCode();

        // 检查是否已存在
        if (extractorByCode.containsKey(code)) {
            log.warn("提取器 {} 已存在，将被覆盖", code);
        }

        // 注册到代码映射
        extractorByCode.put(code, extractor);

        // 注册到分类映射
        String category = metadata.getCategory();
        extractorsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(extractor);

        // 注册到标签映射
        if (metadata.getTags() != null) {
            for (String tag : metadata.getTags()) {
                extractorsByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(extractor);
            }
        }

        log.debug("注册提取器: {} ({})", metadata.getName(), code);
    }

    /**
     * 根据代码获取提取器
     */
    public IEnhancedExtractor getByCode(String code) {
        return extractorByCode.get(code);
    }

    /**
     * 根据代码获取提取器（支持别名）
     * 例如：id_card、idcard、idCard 都能找到身份证提取器
     */
    public IEnhancedExtractor getByCodeWithAlias(String code) {
        // 精确匹配
        IEnhancedExtractor extractor = extractorByCode.get(code);
        if (extractor != null) {
            return extractor;
        }

        // 模糊匹配（处理大小写、下划线、驼峰等）
        String normalizedCode = code.toLowerCase().replace("_", "").replace("-", "");
        for (Map.Entry<String, IEnhancedExtractor> entry : extractorByCode.entrySet()) {
            String entryNormalized = entry.getKey().toLowerCase().replace("_", "").replace("-", "");
            if (entryNormalized.equals(normalizedCode)) {
                log.debug("通过别名匹配: {} -> {}", code, entry.getKey());
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 获取所有提取器
     */
    public Collection<IEnhancedExtractor> getAllExtractors() {
        return Collections.unmodifiableCollection(extractorByCode.values());
    }

    /**
     * 获取所有内置提取器
     */
    public List<IEnhancedExtractor> getBuiltinExtractors() {
        return extractorByCode.values().stream()
            .filter(e -> "builtin".equals(e.getMetadata().getCategory()))
            .collect(Collectors.toList());
    }

    /**
     * 根据分类获取提取器
     */
    public List<IEnhancedExtractor> getByCategory(String category) {
        return extractorsByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 根据标签获取提取器
     */
    public List<IEnhancedExtractor> getByTag(String tag) {
        return extractorsByTag.getOrDefault(tag, Collections.emptyList());
    }

    /**
     * 搜索提取器（按名称、描述、标签）
     */
    public List<IEnhancedExtractor> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>(extractorByCode.values());
        }

        String lowerKeyword = keyword.toLowerCase();
        return extractorByCode.values().stream()
            .filter(e -> matches(e, lowerKeyword))
            .collect(Collectors.toList());
    }

    /**
     * 判断提取器是否匹配关键词
     */
    private boolean matches(IEnhancedExtractor extractor, String keyword) {
        ExtractorMetadata metadata = extractor.getMetadata();

        // 检查代码
        if (metadata.getCode().toLowerCase().contains(keyword)) {
            return true;
        }

        // 检查名称
        if (metadata.getName().toLowerCase().contains(keyword)) {
            return true;
        }

        // 检查描述
        if (metadata.getDescription() != null &&
            metadata.getDescription().toLowerCase().contains(keyword)) {
            return true;
        }

        // 检查标签
        if (metadata.getTags() != null) {
            for (String tag : metadata.getTags()) {
                if (tag.toLowerCase().contains(keyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 获取所有提取器元数据
     */
    public List<ExtractorMetadata> getAllMetadata() {
        return extractorByCode.values().stream()
            .map(IEnhancedExtractor::getMetadata)
            .collect(Collectors.toList());
    }

    /**
     * 获取提取器代码列表
     */
    public Set<String> getCodes() {
        return Collections.unmodifiableSet(extractorByCode.keySet());
    }

    /**
     * 检查提取器是否存在
     */
    public boolean hasExtractor(String code) {
        return getByCodeWithAlias(code) != null;
    }

    /**
     * 获取提取器数量
     */
    public int getExtractorCount() {
        return extractorByCode.size();
    }

    /**
     * 获取分类统计
     */
    public Map<String, Integer> getCategoryStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<IEnhancedExtractor>> entry : extractorsByCategory.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    /**
     * 获取标签统计
     */
    public Map<String, Integer> getTagStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<IEnhancedExtractor>> entry : extractorsByTag.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    /**
     * 打印已注册的提取器
     */
    private void logRegisteredExtractors() {
        if (extractorByCode.isEmpty()) {
            log.warn("没有注册任何提取器");
            return;
        }

        log.info("已注册的提取器列表:");
        for (IEnhancedExtractor extractor : extractorByCode.values()) {
            ExtractorMetadata metadata = extractor.getMetadata();
            log.info("  - {} ({}) [{}] - {}",
                metadata.getName(),
                metadata.getCode(),
                metadata.getCategory(),
                metadata.getDescription()
            );
        }

        // 打印分类统计
        Map<String, Integer> categoryStats = getCategoryStatistics();
        log.info("分类统计: {}", categoryStats);

        // 打印标签统计
        Map<String, Integer> tagStats = getTagStatistics();
        if (!tagStats.isEmpty()) {
            log.info("标签统计: {}", tagStats);
        }
    }

    /**
     * 刷新注册（重新扫描Spring容器）
     * 用于动态加载提取器后的刷新
     */
    public void refresh() {
        log.info("刷新提取器注册中心...");
        initialize();
    }
}
