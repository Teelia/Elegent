package com.datalabeling.service;

import com.datalabeling.entity.SystemPrompt;
import com.datalabeling.repository.SystemPromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 系统提示词服务
 * 提供细粒度的缓存控制和手动刷新机制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPromptService {

    private final SystemPromptRepository systemPromptRepository;

    /**
     * 缓存名称常量
     */
    public static final String CACHE_NAME = "systemPrompts";

    /**
     * 根据ID查询提示词
     */
    @Cacheable(value = CACHE_NAME, key = "#id")
    public Optional<SystemPrompt> findById(Integer id) {
        return systemPromptRepository.findById(id);
    }

    /**
     * 根据代码查询提示词
     */
    @Cacheable(value = CACHE_NAME, key = "'code:' + #code")
    public Optional<SystemPrompt> findByCode(String code) {
        return systemPromptRepository.findByCode(code);
    }

    /**
     * 根据类型查询默认提示词（带缓存）
     */
    @Cacheable(value = CACHE_NAME, key = "'default:' + #promptType")
    public Optional<SystemPrompt> findDefaultByType(String promptType) {
        return systemPromptRepository.findByPromptTypeAndIsSystemDefaultTrueAndIsActiveTrue(promptType);
    }

    /**
     * 根据类型查询所有启用的提示词
     */
    public List<SystemPrompt> findByType(String promptType) {
        return systemPromptRepository.findByPromptTypeAndIsActiveTrue(promptType);
    }

    /**
     * 查询用户可用的提示词（包括全局）
     */
    public List<SystemPrompt> findAvailableForUser(Integer userId) {
        return systemPromptRepository.findByUserOrGlobal(userId);
    }

    /**
     * 查询用户可用的指定类型提示词
     */
    public List<SystemPrompt> findAvailableForUserAndType(Integer userId, String promptType) {
        return systemPromptRepository.findByUserOrGlobalAndType(userId, promptType);
    }

    /**
     * 创建提示词（只清除旧代码的缓存，如果存在）
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'code:' + #prompt.code")
    public SystemPrompt create(SystemPrompt prompt) {
        // 检查代码是否已存在
        if (systemPromptRepository.existsByCode(prompt.getCode())) {
            throw new IllegalArgumentException("提示词代码已存在: " + prompt.getCode());
        }

        SystemPrompt saved = systemPromptRepository.save(prompt);
        log.info("创建系统提示词: id={}, code={}, type={}", saved.getId(), saved.getCode(), saved.getPromptType());

        // 创建后立即缓存新记录
        cachePrompt(saved);

        return saved;
    }

    /**
     * 更新提示词（精确失效相关缓存）
     * 失效：ID缓存、旧代码缓存、新代码缓存、类型默认缓存
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "#id"),
        @CacheEvict(value = CACHE_NAME, key = "'default:' + #prompt.promptType")
    })
    public SystemPrompt update(Integer id, SystemPrompt prompt) {
        SystemPrompt existing = systemPromptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));

        String oldCode = existing.getCode();
        String newCode = prompt.getCode();

        // 检查代码是否被其他记录使用
        if (!oldCode.equals(newCode)
            && systemPromptRepository.existsByCodeAndIdNot(newCode, id)) {
            throw new IllegalArgumentException("提示词代码已被使用: " + newCode);
        }

        // 更新字段
        existing.setName(prompt.getName());
        existing.setCode(newCode);
        existing.setPromptType(prompt.getPromptType());
        existing.setTemplate(prompt.getTemplate());
        existing.setVariables(prompt.getVariables());
        existing.setIsActive(prompt.getIsActive());
        existing.setIsSystemDefault(prompt.getIsSystemDefault());

        SystemPrompt saved = systemPromptRepository.save(existing);
        log.info("更新系统提示词: id={}, code={}, type={}", saved.getId(), saved.getCode(), saved.getPromptType());

        // 清除代码缓存（如果代码发生变化）
        if (!oldCode.equals(newCode)) {
            evictByCode(oldCode);
        }

        // 更新后立即缓存新记录
        cachePrompt(saved);

        return saved;
    }

    /**
     * 删除提示词（精确失效相关缓存）
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CACHE_NAME, key = "#id"),
        @CacheEvict(value = CACHE_NAME, key = "'default:' + #promptType")
    })
    public void delete(Integer id) {
        SystemPrompt prompt = systemPromptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));

        String code = prompt.getCode();
        String promptType = prompt.getPromptType();

        systemPromptRepository.deleteById(id);
        log.info("删除系统提示词: id={}, code={}", id, code);

        // 清除代码缓存
        evictByCode(code);
    }

    /**
     * 获取提示词（优先使用用户指定的，否则使用默认的）
     */
    public SystemPrompt getPromptForUse(Integer promptId, String promptType) {
        if (promptId != null) {
            return systemPromptRepository.findById(promptId)
                .filter(SystemPrompt::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("指定的提示词不存在或未启用: " + promptId));
        }

        // 使用默认提示词（从缓存读取）
        return findDefaultByType(promptType)
            .orElseThrow(() -> new IllegalStateException("没有找到可用的默认" + promptType + "提示词"));
    }

    /**
     * 获取分类提示词
     */
    public SystemPrompt getClassificationPrompt(Integer promptId) {
        return getPromptForUse(promptId, SystemPrompt.PromptType.CLASSIFICATION);
    }

    /**
     * 获取提取提示词
     */
    public SystemPrompt getExtractionPrompt(Integer promptId) {
        return getPromptForUse(promptId, SystemPrompt.PromptType.EXTRACTION);
    }

    /**
     * 获取强化提示词
     */
    public SystemPrompt getEnhancementPrompt(Integer promptId) {
        return getPromptForUse(promptId, SystemPrompt.PromptType.ENHANCEMENT);
    }

    // ==================== 缓存管理方法 ====================

    /**
     * 缓存预热：应用启动时预加载常用提示词
     */
    @PostConstruct
    public void warmUpCache() {
        log.info("开始预热系统提示词缓存...");

        try {
            // 预加载默认分类提示词
            findByCode("classification_with_confidence").ifPresent(prompt ->
                log.debug("预热缓存: classification_with_confidence"));

            // 预加载默认提取提示词
            findByCode("extraction_default").ifPresent(prompt ->
                log.debug("预热缓存: extraction_default"));

            // 预加载默认强化提示词
            findByCode("enhancement_default").ifPresent(prompt ->
                log.debug("预热缓存: enhancement_default"));

            // 预加载所有默认提示词（按类型）
            for (String type : Arrays.asList(
                SystemPrompt.PromptType.CLASSIFICATION,
                SystemPrompt.PromptType.EXTRACTION,
                SystemPrompt.PromptType.ENHANCEMENT
            )) {
                findDefaultByType(type).ifPresent(prompt ->
                    log.debug("预热缓存: default {} prompt - {}", type, prompt.getCode()));
            }

            log.info("系统提示词缓存预热完成");
        } catch (Exception e) {
            log.warn("缓存预热失败（不影响运行）: {}", e.getMessage());
        }
    }

    /**
     * 手动刷新指定提示词的缓存
     *
     * @param id 提示词ID
     * @return 刷新后的提示词
     */
    @CachePut(value = CACHE_NAME, key = "#id")
    public SystemPrompt refreshCache(Integer id) {
        log.info("手动刷新提示词缓存: id={}", id);
        return systemPromptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("提示词不存在: " + id));
    }

    /**
     * 清除指定代码的缓存
     *
     * @param code 提示词代码
     */
    @CacheEvict(value = CACHE_NAME, key = "'code:' + #code")
    public void evictByCode(String code) {
        log.debug("清除提示词缓存: code={}", code);
    }

    /**
     * 手动缓存提示词（用于写操作后主动更新缓存）
     *
     * @param prompt 要缓存的提示词
     */
    @CachePut(value = CACHE_NAME, key = "#prompt.id")
    public SystemPrompt cachePrompt(SystemPrompt prompt) {
        log.debug("缓存提示词: id={}, code={}", prompt.getId(), prompt.getCode());
        return prompt;
    }

    /**
     * 清除所有提示词缓存（谨慎使用）
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAll() {
        log.warn("清除所有系统提示词缓存");
    }

    /**
     * 批量刷新所有默认提示词缓存
     * 用于管理员修改模板后批量刷新
     */
    @Transactional(readOnly = true)
    public void refreshAllDefaultPrompts() {
        log.info("批量刷新所有默认提示词缓存...");

        for (String type : Arrays.asList(
            SystemPrompt.PromptType.CLASSIFICATION,
            SystemPrompt.PromptType.EXTRACTION,
            SystemPrompt.PromptType.ENHANCEMENT,
            SystemPrompt.PromptType.VALIDATION
        )) {
            try {
                findDefaultByType(type).ifPresent(prompt -> {
                    cachePrompt(prompt);
                    log.info("刷新默认提示词缓存: type={}, code={}", type, prompt.getCode());
                });
            } catch (Exception e) {
                log.warn("刷新默认提示词失败: type={}, error={}", type, e.getMessage());
            }
        }

        log.info("批量刷新完成");
    }


    @Scheduled(cron = "0 */5 * * * ?")
    @Transactional(readOnly = true)
    public void scheduledRefreshAllPrompts() {
        log.debug("定时任务：开始刷新所有默认提示词缓存...");

        int successCount = 0;
        int failCount = 0;

        for (String type : Arrays.asList(
            SystemPrompt.PromptType.CLASSIFICATION,
            SystemPrompt.PromptType.EXTRACTION,
            SystemPrompt.PromptType.ENHANCEMENT,
            SystemPrompt.PromptType.VALIDATION
        )) {
            try {
                findDefaultByType(type).ifPresent(prompt -> {
                    cachePrompt(prompt);
                });
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.debug("定时刷新提示词失败: type={}, error={}", type, e.getMessage());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("定时任务：刷新默认提示词缓存完成 - 成功: {}, 失败: {}", successCount, failCount);
        }
    }
}
