package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.config.properties.DeepSeekProperties;
import com.datalabeling.dto.request.CreateModelConfigRequest;
import com.datalabeling.dto.request.UpdateModelConfigRequest;
import com.datalabeling.dto.response.ModelConfigVO;
import com.datalabeling.entity.ModelConfig;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.ModelConfigRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 大模型配置服务（管理员配置 + 运行时读取）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_QWEN = "qwen";

    private static final long CACHE_TTL_MS = 5_000L;

    private final ModelConfigRepository modelConfigRepository;
    private final SyncCryptoService syncCryptoService;
    private final DeepSeekProperties deepSeekProperties;
    private final AuditService auditService;
    private final ModelConcurrencyService modelConcurrencyService;

    // 缓存：按配置ID缓存运行时配置
    private final Map<Integer, CachedConfig> configCache = new ConcurrentHashMap<>();
    
    // 兼容旧代码的缓存
    private volatile LLMRuntimeConfig cachedDeepSeek;
    private volatile long cachedAtMs = 0L;

    /**
     * 获取所有激活的模型配置列表
     */
    public List<ModelConfigVO> listActiveConfigs() {
        List<ModelConfig> configs = modelConfigRepository.findByIsActiveTrueOrderByIsDefaultDescIdAsc();
        return configs.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 获取所有模型配置列表
     */
    public List<ModelConfigVO> listAllConfigs() {
        List<ModelConfig> configs = modelConfigRepository.findAll();
        return configs.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 根据ID获取配置
     */
    public ModelConfigVO getConfigById(Integer id) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));
        return toVO(config);
    }

    /**
     * 获取默认配置
     */
    public ModelConfigVO getDefaultConfig() {
        return modelConfigRepository.findFirstByIsDefaultTrueAndIsActiveTrue()
                .map(this::toVO)
                .orElseGet(this::getDeepSeekConfig);
    }

    /**
     * 获取管理员可见的配置（优先读取DB最新记录；不存在则返回 application.yml 配置）
     */
    public ModelConfigVO getDeepSeekConfig() {
        ModelConfig latest = modelConfigRepository.findFirstByProviderOrderByIdDesc(PROVIDER_DEEPSEEK).orElse(null);
        if (latest == null) {
            return ModelConfigVO.builder()
                    .id(null)
                    .name("DeepSeek 默认配置")
                    .provider(PROVIDER_DEEPSEEK)
                    .providerDisplayName("DeepSeek")
                    .baseUrl(deepSeekProperties.getBaseUrl())
                    .model(deepSeekProperties.getModel())
                    .timeout(deepSeekProperties.getTimeout())
                    .temperature(deepSeekProperties.getTemperature())
                    .maxTokens(deepSeekProperties.getMaxTokens())
                    .retryTimes(deepSeekProperties.getRetryTimes())
                    .maxConcurrency(10)
                    .currentConcurrency(modelConcurrencyService.getCurrentConcurrency(null))
                    .isActive(true)
                    .isDefault(true)
                    .apiKeyConfigured(isApiKeyConfigured(deepSeekProperties.getApiKey()))
                    .fromDb(false)
                    .build();
        }

        return toVO(latest);
    }

    /**
     * 创建新的模型配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO createConfig(CreateModelConfigRequest request, HttpServletRequest httpRequest) {
        // 检查名称是否重复
        if (modelConfigRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置名称已存在");
        }

        ModelConfig config = ModelConfig.builder()
                .name(request.getName())
                .provider(request.getProvider())
                .baseUrl(request.getBaseUrl().trim())
                .model(request.getModel().trim())
                .timeout(request.getTimeout())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .retryTimes(request.getRetryTimes())
                .maxConcurrency(request.getMaxConcurrency())
                .isActive(Boolean.TRUE.equals(request.getIsActive()))
                .isDefault(false)
                .description(request.getDescription())
                .build();

        // 加密API Key
        if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
            config.setApiKeyEncrypted(syncCryptoService.encrypt(request.getApiKey().trim()));
        }

        // 如果设为默认，先清除其他默认
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            modelConfigRepository.clearAllDefaults();
            config.setIsDefault(true);
        }

        config = modelConfigRepository.save(config);
        invalidateAllCache();

        // 记录审计日志
        Map<String, Object> details = new HashMap<>();
        details.put("name", config.getName());
        details.put("provider", config.getProvider());
        details.put("model", config.getModel());
        auditService.record("admin_create_model_config", "model_config", config.getId(), details, httpRequest);

        log.info("创建模型配置: id={}, name={}, provider={}", config.getId(), config.getName(), config.getProvider());
        return toVO(config);
    }

    /**
     * 更新模型配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO updateConfig(Integer id, UpdateModelConfigRequest request, HttpServletRequest httpRequest) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));

        // 检查名称是否重复
        if (request.getName() != null && modelConfigRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置名称已存在");
        }

        if (request.getName() != null) {
            config.setName(request.getName().trim());
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(request.getBaseUrl().trim());
        }
        if (request.getModel() != null) {
            config.setModel(request.getModel().trim());
        }
        if (request.getTimeout() != null) {
            config.setTimeout(request.getTimeout());
        }
        if (request.getTemperature() != null) {
            config.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            config.setMaxTokens(request.getMaxTokens());
        }
        if (request.getRetryTimes() != null) {
            config.setRetryTimes(request.getRetryTimes());
        }
        if (request.getMaxConcurrency() != null) {
            config.setMaxConcurrency(request.getMaxConcurrency());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }

        // 处理API Key
        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            config.setApiKeyEncrypted(null);
        } else if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
            config.setApiKeyEncrypted(syncCryptoService.encrypt(request.getApiKey().trim()));
        }

        // 处理默认设置
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            modelConfigRepository.clearAllDefaults();
            config.setIsDefault(true);
        }

        config = modelConfigRepository.save(config);
        invalidateAllCache();

        // 记录审计日志
        Map<String, Object> details = new HashMap<>();
        details.put("name", config.getName());
        details.put("provider", config.getProvider());
        details.put("model", config.getModel());
        details.put("isActive", config.getIsActive());
        details.put("isDefault", config.getIsDefault());
        auditService.record("admin_update_model_config", "model_config", config.getId(), details, httpRequest);

        log.info("更新模型配置: id={}, name={}", config.getId(), config.getName());
        return toVO(config);
    }

    /**
     * 更新 DeepSeek 配置（兼容旧接口）
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO updateDeepSeekConfig(UpdateModelConfigRequest request, HttpServletRequest httpRequest) {
        ModelConfig current = modelConfigRepository.findFirstByProviderOrderByIdDesc(PROVIDER_DEEPSEEK).orElse(null);
        if (current == null) {
            current = ModelConfig.builder()
                .name("DeepSeek 默认配置")
                .provider(PROVIDER_DEEPSEEK)
                .baseUrl(deepSeekProperties.getBaseUrl())
                .model(deepSeekProperties.getModel())
                .timeout(deepSeekProperties.getTimeout())
                .temperature(deepSeekProperties.getTemperature())
                .maxTokens(deepSeekProperties.getMaxTokens())
                .retryTimes(deepSeekProperties.getRetryTimes())
                .maxConcurrency(10)
                .isActive(true)
                .isDefault(true)
                .build();
        }

        current.setBaseUrl(request.getBaseUrl().trim());
        current.setModel(request.getModel().trim());
        current.setTimeout(request.getTimeout());
        current.setTemperature(request.getTemperature());
        current.setMaxTokens(request.getMaxTokens());
        current.setRetryTimes(request.getRetryTimes());
        if (request.getMaxConcurrency() != null) {
            current.setMaxConcurrency(request.getMaxConcurrency());
        }
        current.setIsActive(Boolean.TRUE.equals(request.getIsActive()));

        if (Boolean.TRUE.equals(request.getClearApiKey())) {
            current.setApiKeyEncrypted(null);
        } else if (request.getApiKey() != null && !request.getApiKey().trim().isEmpty()) {
            current.setApiKeyEncrypted(syncCryptoService.encrypt(request.getApiKey().trim()));
        }

        current = modelConfigRepository.save(current);
        invalidateAllCache();

        Map<String, Object> details = new HashMap<>();
        details.put("provider", current.getProvider());
        details.put("baseUrl", current.getBaseUrl());
        details.put("model", current.getModel());
        details.put("timeout", current.getTimeout());
        details.put("temperature", current.getTemperature());
        details.put("maxTokens", current.getMaxTokens());
        details.put("retryTimes", current.getRetryTimes());
        details.put("isActive", current.getIsActive());
        details.put("apiKeyConfigured", isApiKeyConfigured(current.getApiKeyEncrypted()));
        auditService.record("admin_update_model_config", "model_config", current.getId(), details, httpRequest);

        log.info("管理员更新模型配置: provider={}, active={}", current.getProvider(), current.getIsActive());
        return getDeepSeekConfig();
    }

    /**
     * 删除模型配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Integer id, HttpServletRequest httpRequest) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));

        if (config.getIsDefault()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能删除默认配置");
        }

        modelConfigRepository.delete(config);
        invalidateAllCache();

        // 记录审计日志
        Map<String, Object> details = new HashMap<>();
        details.put("name", config.getName());
        details.put("provider", config.getProvider());
        auditService.record("admin_delete_model_config", "model_config", id, details, httpRequest);

        log.info("删除模型配置: id={}, name={}", id, config.getName());
    }

    /**
     * 设置默认配置
     */
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO setDefaultConfig(Integer id, HttpServletRequest httpRequest) {
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "模型配置不存在"));

        if (!config.getIsActive()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "只能将激活的配置设为默认");
        }

        modelConfigRepository.clearAllDefaults();
        config.setIsDefault(true);
        config = modelConfigRepository.save(config);
        invalidateAllCache();

        // 记录审计日志
        Map<String, Object> details = new HashMap<>();
        details.put("name", config.getName());
        details.put("provider", config.getProvider());
        auditService.record("admin_set_default_model_config", "model_config", id, details, httpRequest);

        log.info("设置默认模型配置: id={}, name={}", id, config.getName());
        return toVO(config);
    }

    /**
     * 根据配置ID获取运行时配置
     */
    public LLMRuntimeConfig getRuntimeConfigById(Integer configId) {
        if (configId == null) {
            return getDeepSeekRuntimeConfig();
        }

        CachedConfig cached = configCache.get(configId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAt < CACHE_TTL_MS) {
            return cached.config;
        }

        ModelConfig config = modelConfigRepository.findById(configId).orElse(null);
        if (config == null || !config.getIsActive()) {
            return getDeepSeekRuntimeConfig();
        }

        LLMRuntimeConfig runtimeConfig = loadRuntimeConfig(config);
        configCache.put(configId, new CachedConfig(runtimeConfig, now));
        return runtimeConfig;
    }

    /**
     * 获取运行时配置（优先DB激活记录；否则使用 application.yml）
     */
    public LLMRuntimeConfig getDeepSeekRuntimeConfig() {
        long now = System.currentTimeMillis();
        LLMRuntimeConfig cached = cachedDeepSeek;
        if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            long now2 = System.currentTimeMillis();
            LLMRuntimeConfig cached2 = cachedDeepSeek;
            if (cached2 != null && now2 - cachedAtMs < CACHE_TTL_MS) {
                return cached2;
            }
            LLMRuntimeConfig fresh = loadDeepSeekRuntimeConfig();
            cachedDeepSeek = fresh;
            cachedAtMs = now2;
            return fresh;
        }
    }

    private LLMRuntimeConfig loadDeepSeekRuntimeConfig() {
        // 优先使用默认配置
        ModelConfig defaultConfig = modelConfigRepository.findFirstByIsDefaultTrueAndIsActiveTrue().orElse(null);
        if (defaultConfig != null) {
            return loadRuntimeConfig(defaultConfig);
        }

        // 其次使用DeepSeek配置
        ModelConfig active = modelConfigRepository.findFirstByProviderAndIsActiveTrueOrderByIdDesc(PROVIDER_DEEPSEEK)
            .orElse(null);
        if (active != null) {
            return loadRuntimeConfig(active);
        }

        // 最后使用application.yml配置
        return LLMRuntimeConfig.builder()
            .configId(null)
            .configName("DeepSeek 默认配置")
            .provider(PROVIDER_DEEPSEEK)
            .apiKey(deepSeekProperties.getApiKey())
            .baseUrl(deepSeekProperties.getBaseUrl())
            .model(deepSeekProperties.getModel())
            .timeout(deepSeekProperties.getTimeout())
            .temperature(deepSeekProperties.getTemperature())
            .maxTokens(deepSeekProperties.getMaxTokens())
            .retryTimes(deepSeekProperties.getRetryTimes())
            .maxConcurrency(10)
            .build();
    }

    private LLMRuntimeConfig loadRuntimeConfig(ModelConfig config) {
        String apiKey = null;
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().trim().isEmpty()) {
            try {
                apiKey = syncCryptoService.decrypt(config.getApiKeyEncrypted());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "模型配置API Key解密失败，请检查 crypto-key/jwt.secret 是否变更");
            }
        }

        return LLMRuntimeConfig.builder()
            .configId(config.getId())
            .configName(config.getName())
            .provider(config.getProvider())
            .apiKey(apiKey)
            .baseUrl(config.getBaseUrl())
            .model(config.getModel())
            .timeout(config.getTimeout())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .retryTimes(config.getRetryTimes())
            .maxConcurrency(config.getMaxConcurrency())
            .build();
    }

    private ModelConfigVO toVO(ModelConfig config) {
        return ModelConfigVO.builder()
                .id(config.getId())
                .name(config.getName())
                .provider(config.getProvider())
                .providerDisplayName(config.getProviderDisplayName())
                .baseUrl(config.getBaseUrl())
                .model(config.getModel())
                .timeout(config.getTimeout())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .retryTimes(config.getRetryTimes())
                .maxConcurrency(config.getMaxConcurrency())
                .currentConcurrency(modelConcurrencyService.getCurrentConcurrency(config.getId()))
                .isActive(config.getIsActive())
                .isDefault(config.getIsDefault())
                .description(config.getDescription())
                .apiKeyConfigured(isApiKeyConfigured(config.getApiKeyEncrypted()))
                .fromDb(true)
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    /**
     * 将VO转换为运行时配置
     */
    public LLMRuntimeConfig toRuntimeConfig(ModelConfigVO vo) {
        // VO不包含apiKey，需要从数据库加载
        String apiKey = null;
        ModelConfig entity = modelConfigRepository.findById(vo.getId()).orElse(null);
        if (entity != null && entity.getApiKeyEncrypted() != null) {
            apiKey = syncCryptoService.decrypt(entity.getApiKeyEncrypted());
        }

        return LLMRuntimeConfig.builder()
                .configId(vo.getId())
                .configName(vo.getName())
                .provider(vo.getProvider())
                .apiKey(apiKey)
                .baseUrl(vo.getBaseUrl())
                .model(vo.getModel())
                .timeout(vo.getTimeout())
                .temperature(vo.getTemperature())
                .maxTokens(vo.getMaxTokens())
                .retryTimes(vo.getRetryTimes())
                .maxConcurrency(entity != null ? entity.getMaxConcurrency() : vo.getMaxConcurrency())
                .build();
    }

    private boolean isApiKeyConfigured(String value) {
        return value != null && !value.trim().isEmpty() && !"your_deepseek_api_key".equalsIgnoreCase(value.trim());
    }

    private void invalidateAllCache() {
        cachedAtMs = 0L;
        cachedDeepSeek = null;
        configCache.clear();
    }

    /**
     * 缓存包装类
     */
    private static class CachedConfig {
        final LLMRuntimeConfig config;
        final long cachedAt;

        CachedConfig(LLMRuntimeConfig config, long cachedAt) {
            this.config = config;
            this.cachedAt = cachedAt;
        }
    }

    /**
     * 通用LLM运行时配置（兼容多种提供商）
     */
    @Getter
    public static class LLMRuntimeConfig {
        private final Integer configId;
        private final String configName;
        private final String provider;
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final Integer timeout;
        private final Double temperature;
        private final Integer maxTokens;
        private final Integer retryTimes;
        private final Integer maxConcurrency;

        @lombok.Builder
        private LLMRuntimeConfig(Integer configId, String configName, String provider,
                                 String apiKey, String baseUrl, String model, Integer timeout,
                                 Double temperature, Integer maxTokens, Integer retryTimes, Integer maxConcurrency) {
            this.configId = configId;
            this.configName = configName;
            this.provider = provider;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
            this.timeout = timeout;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.retryTimes = retryTimes;
            this.maxConcurrency = maxConcurrency;
        }
    }

    /**
     * 兼容旧代码的别名
     */
    @Deprecated
    public static class DeepSeekRuntimeConfig extends LLMRuntimeConfig {
        @lombok.Builder(builderMethodName = "deepSeekBuilder")
        private DeepSeekRuntimeConfig(String apiKey, String baseUrl, String model, Integer timeout,
                                      Double temperature, Integer maxTokens, Integer retryTimes, Integer maxConcurrency) {
            super(null, "DeepSeek", PROVIDER_DEEPSEEK, apiKey, baseUrl, model, timeout, temperature, maxTokens, retryTimes, maxConcurrency);
        }
    }
}
