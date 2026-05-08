package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.exception.LLMCommunicationException;
import com.datalabeling.exception.LLMAuthenticationException;
import com.datalabeling.exception.LLMRateLimitException;
import com.datalabeling.exception.LLMSerializationException;
import com.datalabeling.exception.LLMTimeoutException;
import com.datalabeling.service.extraction.NegativeConditionPreprocessor;
import com.datalabeling.service.extraction.NegativeConditionPreprocessor.PreprocessResult;
import com.datalabeling.service.extraction.NumberEvidence;
import com.datalabeling.service.model.LabelJudgeResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek（OpenAI兼容）调用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final OkHttpClient okHttpClient;
    private final ModelConfigService modelConfigService;
    private final ModelConcurrencyService modelConcurrencyService;
    private final SystemPromptService systemPromptService;
    private final PromptTemplateEngine promptTemplateEngine;
    private final NegativeConditionPreprocessor negativeConditionPreprocessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Integer, OkHttpClient> clientByTimeout = new ConcurrentHashMap<>();

    /**
     * 获取系统提示词（从数据库）- 按类型查询默认提示词
     *
     * @param promptType 提示词类型
     * @return 提示词模板内容，如果找不到则返回默认值
     */
    private String getSystemPromptByType(String promptType) {
        try {
            return systemPromptService.findDefaultByType(promptType)
                .map(com.datalabeling.entity.SystemPrompt::getTemplate)
                .orElse(getDefaultPromptByType(promptType));
        } catch (Exception e) {
            log.warn("获取系统提示词失败: {}，使用默认值: {}", promptType, e.getMessage());
            return getDefaultPromptByType(promptType);
        }
    }

    /**
     * 获取系统提示词（从数据库）- 按代码查询（保留用于兼容）
     *
     * @param promptCode 提示词代码
     * @return 提示词模板内容，如果找不到则返回默认值
     */
    private String getSystemPromptTemplate(String promptCode) {
        try {
            return systemPromptService.findByCode(promptCode)
                .filter(com.datalabeling.entity.SystemPrompt::getIsActive)
                .map(com.datalabeling.entity.SystemPrompt::getTemplate)
                .orElse(getDefaultPrompt(promptCode));
        } catch (Exception e) {
            log.warn("获取系统提示词失败: {}，使用默认值: {}", promptCode, e.getMessage());
            return getDefaultPrompt(promptCode);
        }
    }

    /**
     * 获取默认提示词（兜底）- 按类型
     */
    private String getDefaultPromptByType(String promptType) {
        switch (promptType) {
            case com.datalabeling.entity.SystemPrompt.PromptType.CLASSIFICATION:
                return PromptTemplateEngine.getDefaultClassificationPrompt();
            case com.datalabeling.entity.SystemPrompt.PromptType.ENHANCEMENT:
                return PromptTemplateEngine.getDefaultEnhancementPrompt();
            case com.datalabeling.entity.SystemPrompt.PromptType.EXTRACTION:
                return getDefaultExtractionPrompt();
            default:
                return "你是专业的数据标注助手。";
        }
    }

    /**
     * 获取默认提示词（兜底）- 按代码（保留用于兼容）
     */
    private String getDefaultPrompt(String promptCode) {
        switch (promptCode) {
            case "default_classification":
                return PromptTemplateEngine.getDefaultClassificationPrompt();
            case "default_enhancement":
                return PromptTemplateEngine.getDefaultEnhancementPrompt();
            case "default_free_form_extraction":
                return getDefaultFreeFormExtractionPrompt();
            case "default_extraction":
                return getDefaultExtractionPrompt();
            case "classification_with_confidence":
                return getClassificationWithConfidencePrompt();
            default:
                return "你是专业的数据标注助手。";
        }
    }

    /**
     * 对单条数据按指定标签进行判断（仅返回"是/否"）
     */
    public String judge(Label label, Map<String, Object> rowData) {
        ModelConfigService.LLMRuntimeConfig config = modelConfigService.getDeepSeekRuntimeConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.LLM_API_ERROR, "大模型 API Key 未配置");
        }

        String prompt = buildPrompt(label, rowData);
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        // 使用数据库中的默认分类提示词（按类型查询）
        String systemPrompt = getSystemPromptByType(com.datalabeling.entity.SystemPrompt.PromptType.CLASSIFICATION);
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());

        // 检测是否是千问的仅流式模型（如 qwq-32b），自动启用流式
        boolean requireStream = isQwenStreamOnlyModel(config.getModel());
        requestBody.put("stream", requireStream);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LLMSerializationException(
                ErrorCode.SYSTEM_ERROR,
                "请求JSON序列化失败: " + e.getMessage(),
                e,
                "REQUEST"
            );
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        // 记录响应体内容用于诊断
                        String errorBody = response.body() != null ? response.body().string() : "";
                        log.error("[DeepSeekService.judge] API调用失败: HTTP={}, url={}, model={}, response={}",
                            code, url, config.getModel(), errorBody);

                        if (code == 401 || code == 403) {
                            throw new LLMAuthenticationException(
                                ErrorCode.LLM_API_ERROR,
                                "API认证失败: HTTP " + code + ", 详情: " + errorBody
                            );
                        }
                        if (code == 429) {
                            throw new LLMRateLimitException(
                                ErrorCode.LLM_RATE_LIMIT,
                                "大模型调用频率超限"
                            );
                        }
                        throw new LLMCommunicationException(
                            ErrorCode.LLM_API_ERROR,
                            "大模型调用失败: HTTP " + code + ", 详情: " + errorBody,
                            code
                        );
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    return parseYesNo(body);
                }
            } catch (LLMRateLimitException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e.getRetryDelayMs() > 0 ? e.getRetryDelayMs() : getBackoffDelay(attempt);
                sleepBackoffWithDelay(delay);
            } catch (LLMAuthenticationException e) {
                // 认证错误不重试，直接抛出
                throw e;
            } catch (LLMCommunicationException | LLMTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e instanceof LLMTimeoutException ?
                    ((LLMTimeoutException) e).getRetryDelayMs() :
                    e.getRetryDelayMs();
                if (delay > 0) {
                    sleepBackoffWithDelay(delay);
                } else {
                    sleepBackoff(attempt);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw new LLMTimeoutException(
                        ErrorCode.LLM_API_ERROR,
                        "请求超时",
                        config.getTimeout() != null ? config.getTimeout().longValue() : -1L,
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    throw new LLMCommunicationException(
                        ErrorCode.LLM_API_ERROR,
                        "网络通信失败: " + e.getMessage(),
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("大模型调用未知异常: {}", e.getMessage(), e);
                    throw new BusinessException(ErrorCode.LLM_API_ERROR, "大模型调用异常: " + e.getMessage());
                }
                sleepBackoff(attempt);
            }
        }

        throw new BusinessException(ErrorCode.LLM_API_ERROR, "大模型调用失败");
    }

    /**
     * 获取退避延迟时间（毫秒）
     */
    private long getBackoffDelay(int attempt) {
        return 500L * (1L << Math.min(attempt, 3));
    }

    /**
     * 使用指定延迟时间进行退避
     */
    private void sleepBackoffWithDelay(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 对单条数据按指定标签进行判断（返回包含信心度的结果）
     *
     * @param label 标签定义
     * @param rowData 数据行
     * @return 包含结果、信心度、原因的判断结果
     */
    public LabelJudgeResult judgeWithConfidence(Label label, Map<String, Object> rowData) {
        long startTime = System.currentTimeMillis();
        
        ModelConfigService.LLMRuntimeConfig config = modelConfigService.getDeepSeekRuntimeConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return LabelJudgeResult.failure("大模型 API Key 未配置", System.currentTimeMillis() - startTime);
        }

        String prompt = buildPromptWithConfidence(label, rowData, null);
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", buildSystemPromptWithConfidence()));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());

        // 检测是否是千问的仅流式模型（如 qwq-32b），自动启用流式
        boolean requireStream = isQwenStreamOnlyModel(config.getModel());
        requestBody.put("stream", requireStream);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            return LabelJudgeResult.failure("请求JSON序列化失败: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        if (code == 401 || code == 403) {
                            return LabelJudgeResult.failure("API认证失败: HTTP " + code, System.currentTimeMillis() - startTime);
                        }
                        if (code == 429) {
                            if (attempt >= maxRetry) {
                                return LabelJudgeResult.failure("大模型调用频率超限", System.currentTimeMillis() - startTime);
                            }
                            long delay = 10000L; // 10秒延迟
                            sleepBackoffWithDelay(delay);
                            continue;
                        }
                        if (attempt >= maxRetry) {
                            return LabelJudgeResult.failure("大模型调用失败: HTTP " + code, System.currentTimeMillis() - startTime);
                        }
                        sleepBackoff(attempt);
                        continue;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    return parseResultWithConfidence(body, System.currentTimeMillis() - startTime);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    return LabelJudgeResult.failure("请求超时", System.currentTimeMillis() - startTime);
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    log.error("网络通信失败: {}", e.getMessage(), e);
                    return LabelJudgeResult.failure("网络通信失败: " + e.getMessage(), System.currentTimeMillis() - startTime);
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("大模型调用未知异常: {}", e.getMessage(), e);
                    return LabelJudgeResult.failure("大模型调用异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
                }
                sleepBackoff(attempt);
            }
        }

        return LabelJudgeResult.failure("大模型调用失败", System.currentTimeMillis() - startTime);
    }

    /**
     * 对单条数据按指定标签进行判断（支持自定义配置和推理过程）
     */
    public Object judge(Label label, Map<String, Object> rowData, ModelConfigService.LLMRuntimeConfig config, boolean includeReasoning) {
        return judge(label, rowData, null, config, includeReasoning);
    }

    /**
     * 对单条数据按指定标签进行判断（支持预处理结果）
     *
     * @param label 标签定义
     * @param rowData 数据行
     * @param preprocessorResult 规则预处理结果（可为null）
     * @param config 模型配置
     * @param includeReasoning 是否包含推理过程
     * @return 判断结果
     */
    public Object judge(Label label, Map<String, Object> rowData, String preprocessorResult,
                        ModelConfigService.LLMRuntimeConfig config, boolean includeReasoning) {
        log.info("[DeepSeekService.judge] 开始: labelName={}, includeReasoning={}, hasPreprocessor={}",
            label.getName(), includeReasoning, preprocessorResult != null);

        if (config == null) {
            config = modelConfigService.getDeepSeekRuntimeConfig();
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.LLM_API_ERROR, "API Key 未配置");
        }

        String prompt = buildPrompt(label, rowData, preprocessorResult);
        log.info("[DeepSeekService.judge] 提示词构建完成: prompt长度={}", prompt != null ? prompt.length() : 0);
        log.info("[DeepSeekService.judge] 提示词内容: {}", prompt);

        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        String systemPrompt;
        if (includeReasoning) {
            systemPrompt = buildSystemPromptWithConfidenceAndReasoning();
        } else {
            systemPrompt = "你是专业的数据标注助手，请根据规则严格判断数据。请仅回答\"是\"或\"否\"。";
        }
        
        messages.add(createMessage("system", systemPrompt));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        if (includeReasoning) {
            Map<String, String> responseFormat = new HashMap<>();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);
        }

        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());

        // 检测是否是千问的仅流式模型（如 qwq-32b），自动启用流式
        boolean requireStream = isQwenStreamOnlyModel(config.getModel());
        requestBody.put("stream", requireStream);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LLMSerializationException(
                ErrorCode.SYSTEM_ERROR,
                "请求JSON序列化失败: " + e.getMessage(),
                e,
                "REQUEST"
            );
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        // 记录响应体内容用于诊断
                        String errorBody = response.body() != null ? response.body().string() : "";
                        log.error("[DeepSeekService.judge] API调用失败: HTTP={}, url={}, model={}, response={}",
                            code, url, config.getModel(), errorBody);

                        if (code == 401 || code == 403) {
                            throw new LLMAuthenticationException(
                                ErrorCode.LLM_API_ERROR,
                                "API认证失败: HTTP " + code + ", 详情: " + errorBody
                            );
                        }
                        if (code == 429) {
                            throw new LLMRateLimitException(
                                ErrorCode.LLM_RATE_LIMIT,
                                "大模型调用频率超限"
                            );
                        }
                        throw new LLMCommunicationException(
                            ErrorCode.LLM_API_ERROR,
                            "大模型调用失败: HTTP " + code + ", 详情: " + errorBody,
                            code
                        );
                    }

                    // 处理流式或非流式响应
                    String body = response.body() != null ? response.body().string() : "";
                    String actualResponse = body;

                    // 如果启用了流式，需要从 SSE 格式中提取完整响应
                    if (requireStream && body != null && !body.isEmpty()) {
                        actualResponse = extractCompleteResponseFromStream(body);
                        log.info("[DeepSeekService.judge] 流式响应已合并: 原始长度={}, 合并后长度={}",
                            body.length(), actualResponse != null ? actualResponse.length() : 0);
                    }

                    log.info("[DeepSeekService.judge] LLM响应完成: body长度={}", actualResponse != null ? actualResponse.length() : 0);
                    log.info("[DeepSeekService.judge] LLM响应内容: {}", actualResponse);

                    if (includeReasoning) {
                        return parseReasoningResultWithConfidence(actualResponse);
                    } else {
                        return parseYesNo(actualResponse);
                    }
                }
            } catch (LLMRateLimitException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e.getRetryDelayMs() > 0 ? e.getRetryDelayMs() : getBackoffDelay(attempt);
                sleepBackoffWithDelay(delay);
            } catch (LLMAuthenticationException e) {
                // 认证错误不重试，直接抛出
                throw e;
            } catch (LLMCommunicationException | LLMTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e instanceof LLMTimeoutException ?
                    ((LLMTimeoutException) e).getRetryDelayMs() :
                    e.getRetryDelayMs();
                if (delay > 0) {
                    sleepBackoffWithDelay(delay);
                } else {
                    sleepBackoff(attempt);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw new LLMTimeoutException(
                        ErrorCode.LLM_API_ERROR,
                        "请求超时",
                        config.getTimeout() != null ? config.getTimeout().longValue() : -1L,
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    throw new LLMCommunicationException(
                        ErrorCode.LLM_API_ERROR,
                        "网络通信失败: " + e.getMessage(),
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("大模型调用未知异常: {}", e.getMessage(), e);
                    throw new BusinessException(ErrorCode.LLM_API_ERROR, "调用异常: " + e.getMessage());
                }
                sleepBackoff(attempt);
            }
        }
        throw new BusinessException(ErrorCode.LLM_API_ERROR, "重试次数耗尽");
    }

    /**
     * 自由提取模式：根据标签描述让大模型自由提取信息
     * 不需要预先指定提取字段，直接返回大模型的回复内容
     *
     * 【优化】对于规则明确且可验证的否定条件任务，优先使用预处理器输出确定性结果（可审计，不等同业务口径“100%准确”）
     *
     * @param label 提取类型标签
     * @param rowData 数据行
     * @param config 模型配置
     * @return 提取结果，包含 result（提取内容）, confidence, success
     */
    public Map<String, Object> extractFreeForm(Label label, Map<String, Object> rowData, ModelConfigService.LLMRuntimeConfig config) {
        long startTime = System.currentTimeMillis();

        // [详细日志] 记录任务开始
        log.info(repeatString("=", 80));
        log.info("[extractFreeForm] 任务开始");
        log.info("[extractFreeForm] 标签信息: name={}, description={}", label.getName(), label.getDescription());
        log.info("[extractFreeForm] 数据摘要: 列数={}, 数据={}",
            String.valueOf(rowData.size()),
            abbreviateMap(rowData, 3)
        );
        log.info(repeatString("-", 80));

        if (config == null) {
            config = modelConfigService.getDeepSeekRuntimeConfig();
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            log.error("[extractFreeForm] API Key 未配置");
            return createFreeFormFailure("API Key 未配置", startTime);
        }

        // 【优化】优先尝试使用规则预处理器处理否定条件任务
        log.debug("[extractFreeForm] 尝试规则预处理器...");
        PreprocessResult preprocessResult = negativeConditionPreprocessor.preprocess(label, rowData);

        if (preprocessResult != null) {
            log.debug("[extractFreeForm] 预处理器返回: canHandle={}, isEmpty={}, result={}",
                String.valueOf(preprocessResult.canHandle()),
                String.valueOf(preprocessResult.isEmpty()),
                abbreviate(preprocessResult.getResult(), 100)
            );

            if (preprocessResult.canHandle()) {
                log.info("[extractFreeForm] 规则预处理器成功处理任务");
                log.info("[extractFreeForm] 结果: result={}, confidence={}, reasoning={}",
                    preprocessResult.getResult(),
                    String.valueOf(preprocessResult.getConfidence()),
                    abbreviate(preprocessResult.getReasoning(), 200)
                );
                log.info(repeatString("=", 80));
                return preprocessResult.toResponseMap(System.currentTimeMillis() - startTime);
            }
        } else {
            log.debug("[extractFreeForm] 预处理器返回 null");
        }

        // 预处理器无法处理，使用 LLM
        log.debug("规则预处理器无法处理，使用 LLM: label={}", label.getName());
        String prompt = buildFreeFormExtractionPrompt(label, rowData);
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", buildFreeFormSystemPrompt()));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        // 强制 JSON 输出格式
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());

        // 检测是否是千问的仅流式模型（如 qwq-32b），自动启用流式
        boolean requireStream = isQwenStreamOnlyModel(config.getModel());
        requestBody.put("stream", requireStream);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            return createFreeFormFailure("请求JSON序列化失败: " + e.getMessage(), startTime);
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        if (code == 401 || code == 403) {
                            return createFreeFormFailure("API认证失败: HTTP " + code, startTime);
                        }
                        if (code == 429) {
                            if (attempt >= maxRetry) {
                                return createFreeFormFailure("大模型调用频率超限", startTime);
                            }
                            long delay = 10000L; // 10秒延迟
                            sleepBackoffWithDelay(delay);
                            continue;
                        }
                        if (attempt >= maxRetry) {
                            return createFreeFormFailure("大模型调用失败: HTTP " + code, startTime);
                        }
                        sleepBackoff(attempt);
                        continue;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    return parseFreeFormResult(body, label, startTime);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    return createFreeFormFailure("请求超时", startTime);
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    log.error("网络通信失败: {}", e.getMessage(), e);
                    return createFreeFormFailure("网络通信失败: " + e.getMessage(), startTime);
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("自由提取未知异常: {}", e.getMessage(), e);
                    return createFreeFormFailure("调用异常: " + e.getMessage(), startTime);
                }
                sleepBackoff(attempt);
            }
        }

        return createFreeFormFailure("重试次数耗尽", startTime);
    }

    /**
     * 构建自由提取的系统提示词
     */
    private String buildFreeFormSystemPrompt() {
        return getSystemPromptTemplate("default_free_form_extraction");
    }

    /**
       * 构建自由提取的用户提示词（针对 DeepSeek 70B 优化，智能检测否定条件）
       *
       * 【增强策略】数据建模 + LLM 混合方案：
       * 1. 先使用规则侧“号码证据建模”进行预识别
       * 2. 将证据结果作为上下文传递给 DeepSeek 70B
       * 3. 大模型基于证据进行最终判断（不得凭空生成号码）
       */
      private String buildFreeFormExtractionPrompt(Label label, Map<String, Object> rowData) {
        StringBuilder sb = new StringBuilder();
        sb.append("【提取要求】: ").append(label.getDescription()).append("\n\n");

        String desc = label.getDescription().toLowerCase();
        String dataString = convertRowDataToString(rowData);

        // 检测是否是否定条件类任务
        boolean isNegativeCondition = desc.contains("不满足") || desc.contains("不是") ||
            desc.contains("非") || desc.contains("错误的") || desc.contains("异常的") ||
            desc.contains("无效") || desc.contains("不符");

        // 检测是否是包含/查找类型的需求
        boolean isExactMatch = desc.contains("包含") || desc.contains("查找") || desc.contains("找出") ||
            desc.contains("搜索") || desc.contains("匹配") || desc.contains("contain");

        // 检测是否是卡号识别相关任务
        boolean isCardNumberTask = desc.contains("身份证") || desc.contains("银行卡") ||
            desc.contains("手机号") || desc.contains("卡号");

          // 【数据建模层】如果涉及卡号识别，先进行智能识别
          if (isCardNumberTask) {
              NumberEvidence evidence =
                  negativeConditionPreprocessor.getNumberEvidenceExtractor().extract(dataString);
  
              sb.append("【数据建模预识别结果】\n");
              sb.append("(基于规则的号码证据建模，供参考)\n");
              sb.append("================================================================\n");
  
              if (evidence.getNumbers() == null || evidence.getNumbers().isEmpty()) {
                  sb.append("\n未识别到任何号码候选\n");
              } else {
                  // 按类型聚合展示（默认脱敏显示）
                  Map<String, List<NumberEvidence.NumberCandidate>> byType = new LinkedHashMap<>();
                  for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
                      String type = n.getType() != null ? n.getType() : "UNKNOWN";
                      byType.computeIfAbsent(type, k -> new ArrayList<>()).add(n);
                  }

                  for (Map.Entry<String, List<NumberEvidence.NumberCandidate>> entry : byType.entrySet()) {
                      String type = entry.getKey();
                      List<NumberEvidence.NumberCandidate> items = entry.getValue();
                      sb.append("\n").append(type).append(" (共").append(items.size()).append("个):\n");
                      for (NumberEvidence.NumberCandidate n : items) {
                          String show = n.getMaskedValue() != null && !n.getMaskedValue().isEmpty()
                              ? n.getMaskedValue()
                              : n.getValue();
                          sb.append("  - ").append(n.getId()).append(": ").append(show)
                              .append(" (长度: ").append(n.getLength()).append(")\n");
                      }
                  }
              }
  
              sb.append("\n================================================================\n");
              sb.append("【重要】以上为规则侧证据建模结果，请优先基于这些信息进行判断。\n");
              sb.append("【约束】如需输出任何号码：只能从上述列表中选择，并在 reasoning 中引用对应证据ID（例如 n1）。禁止输出列表外号码。\n\n");
          }

        if (isNegativeCondition) {
            // 否定条件任务：添加强化提示
            sb.append("【关键提醒】这是一个【否定条件】提取任务，请务必理解：\n");
            sb.append("==================================================================\n");
            sb.append("1. 你需要提取的是【不符合条件】的内容，而不是【符合条件】的内容\n");
            sb.append("2. 例如：\"提取不满足18位的身份证号\"\n");
            sb.append("   - 正确做法：只返回长度不是18位的号码（17位、19位等）\n");
            sb.append("   - 错误做法：返回18位的号码并在reasoning中说明它是18位的\n");
            sb.append("3. 如果所有内容都符合条件（即没有不符合条件的），必须返回\"无\"\n");
            sb.append("4. 绝对不要：提取了符合条件的内容，然后在reasoning中说它符合条件\n");
            sb.append("==================================================================\n\n");

            // 提取具体的否定条件说明
            String condition = extractNegativeCondition(label.getDescription());
            if (condition != null) {
                sb.append("【条件分析】\n");
                sb.append("- 你要提取的是: ").append(condition).append("\n");
                sb.append("- 不要提取的内容: 与之相反的（符合条件的内容）\n");
                sb.append("- 如果数据中没有满足上述条件的内容，result 必须是 \"无\"\n\n");
            }
        } else if (isExactMatch) {
            sb.append("【注意】这是一个精确匹配任务，请严格按照以下规则执行：\n");
            sb.append("1. 必须在数据中找到【完全一致】的字符串才算匹配成功\n");
            sb.append("2. 不要进行语义理解或近义词匹配\n");
            sb.append("3. 如果数据中没有完全包含指定的字符串，必须返回\"未提取到相关信息\"\n");
            sb.append("4. 在reasoning中明确说明是否找到了完全匹配的内容\n\n");
        }

        // 根据是否有关注列，决定传递哪些数据
        Map<String, Object> dataToSend = filterDataByFocusColumns(label, rowData);

        if (label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
            sb.append("【关注列】: ").append(String.join(", ", label.getFocusColumns())).append("\n\n");
        }
        sb.append("【数据内容】:\n");
        try {
            sb.append(objectMapper.writeValueAsString(dataToSend));
        } catch (Exception e) {
            sb.append(String.valueOf(dataToSend));
        }

        // 对于否定条件，再次强调输出格式
        if (isNegativeCondition) {
            sb.append("\n\n【输出格式再次强调】\n");
            sb.append("请确保你的输出符合以下要求：\n");
            sb.append("- 如果找到不符合条件的内容：result = 找到的内容\n");
            sb.append("- 如果所有内容都符合条件（即没找到不符合条件的）：result = \"无\"\n");
            sb.append("- 不要输出符合条件的内容作为结果！");
        }

        return sb.toString();
    }

    /**
     * 从标签描述中提取否定条件的具体说明
     * 例如："提取不满足18位的身份证号" -> "长度不是18位的身份证号"
     */
    private String extractNegativeCondition(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }

        // 匹配 "不满足XX的" 模式
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("不满足(.+?)的");
        java.util.regex.Matcher matcher = pattern.matcher(description);
        if (matcher.find()) {
            return "不" + matcher.group(1) + "的";
        }

        // 匹配 "不是XX的" 模式
        pattern = java.util.regex.Pattern.compile("不是(.+?)的");
        matcher = pattern.matcher(description);
        if (matcher.find()) {
            return "不是" + matcher.group(1) + "的";
        }

        // 匹配 "错误的XX" 模式
        pattern = java.util.regex.Pattern.compile("错误的(.+)");
        matcher = pattern.matcher(description);
        if (matcher.find()) {
            return "错误的" + matcher.group(1);
        }

        return null;
    }
    
    /**
     * 根据标签的关注列过滤数据
     * 如果设置了关注列，只返回关注列的数据；否则返回全部数据
     *
     * @param label 标签（包含关注列配置）
     * @param rowData 原始数据行
     * @return 过滤后的数据
     */
    private Map<String, Object> filterDataByFocusColumns(Label label, Map<String, Object> rowData) {
        if (label.getFocusColumns() == null || label.getFocusColumns().isEmpty()) {
            // 没有设置关注列，返回全部数据
            return rowData;
        }
        
        // 构建列名映射（支持大小写不敏感和去除空格的匹配）
        Map<String, String> normalizedKeyMap = new HashMap<>();
        for (String key : rowData.keySet()) {
            normalizedKeyMap.put(normalizeColumnName(key), key);
        }
        
        // 只提取关注列的数据
        Map<String, Object> filteredData = new LinkedHashMap<>();
        List<String> unmatchedColumns = new ArrayList<>();
        
        for (String column : label.getFocusColumns()) {
            String normalizedColumn = normalizeColumnName(column);
            
            // 先尝试精确匹配
            if (rowData.containsKey(column)) {
                filteredData.put(column, rowData.get(column));
            }
            // 再尝试标准化后的匹配（大小写不敏感、去除空格）
            else if (normalizedKeyMap.containsKey(normalizedColumn)) {
                String actualKey = normalizedKeyMap.get(normalizedColumn);
                filteredData.put(actualKey, rowData.get(actualKey));
                log.debug("关注列 '{}' 通过标准化匹配到 '{}'", column, actualKey);
            }
            else {
                unmatchedColumns.add(column);
            }
        }
        
        // 记录未匹配的列
        if (!unmatchedColumns.isEmpty()) {
            log.warn("以下关注列在数据中未找到匹配: {}，数据中的列名: {}",
                unmatchedColumns, rowData.keySet());
        }
        
        // 如果过滤后没有数据（可能是列名不匹配），返回原始数据以避免空数据
        if (filteredData.isEmpty()) {
            log.warn("所有关注列 {} 在数据中均未找到匹配，将使用全部数据。数据中的列名: {}",
                label.getFocusColumns(), rowData.keySet());
            return rowData;
        }
        
        log.debug("关注列过滤: 原始列数={}, 过滤后列数={}, 过滤后的列={}",
            rowData.size(), filteredData.size(), filteredData.keySet());
        
        return filteredData;
    }
    
    /**
     * 标准化列名（用于模糊匹配）
     * 转小写、去除首尾空格、去除中间多余空格
     */
    private String normalizeColumnName(String columnName) {
        if (columnName == null) {
            return "";
        }
        return columnName.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * 解析自由提取结果（针对 DeepSeek 70B 优化，添加否定条件后验证）
     */
    private Map<String, Object> parseFreeFormResult(String responseBody, Label label, long startTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            if (content == null || content.trim().isEmpty()) {
                return createFreeFormFailure("大模型返回内容为空", startTime);
            }

            // 尝试解析 JSON 格式
            String jsonContent = extractJson(content);
            if (jsonContent != null) {
                try {
                    JsonNode resultNode = objectMapper.readTree(jsonContent);
                    String result = resultNode.path("result").asText("");
                    int confidence = resultNode.path("confidence").asInt(80);
                    String reasoning = resultNode.path("reasoning").asText("");

                    // 对否定条件任务进行后处理验证
                    if (label != null && isNegativeConditionTask(label.getDescription())) {
                        Map<String, Object> validationResult = validateNegativeConditionResult(
                            result, reasoning, label.getDescription());
                        if (Boolean.TRUE.equals(validationResult.get("needs_correction"))) {
                            log.warn("否定条件任务检测结果异常，自动修正: 原结果={}, 修正原因={}",
                                result, validationResult.get("correction_reason"));
                            result = (String) validationResult.get("corrected_result");
                            confidence = Math.min(confidence, 60); // 降低修正后的信心度
                            reasoning = reasoning + " [系统后验证修正: " +
                                validationResult.get("correction_reason") + "]";
                        }
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("result", result.isEmpty() ? content.trim() : result);
                    response.put("success", true);
                    response.put("confidence", Math.min(100, Math.max(0, confidence)));
                    response.put("reasoning", reasoning);
                    response.put("durationMs", System.currentTimeMillis() - startTime);
                    return response;
                } catch (Exception e) {
                    log.debug("JSON解析失败，使用原始内容: {}", e.getMessage());
                }
            }

            // 回退：直接使用原始内容
            String result = content.trim();
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("success", true);
            response.put("confidence", 80); // 自由提取默认80%信心度
            response.put("reasoning", ""); // 无法解析时没有 reasoning
            response.put("durationMs", System.currentTimeMillis() - startTime);

            return response;
        } catch (Exception e) {
            log.warn("解析自由提取结果失败: {}", e.getMessage());
            return createFreeFormFailure("解析响应失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 判断是否是否定条件任务
     */
    private boolean isNegativeConditionTask(String description) {
        if (description == null) {
            return false;
        }
        String desc = description.toLowerCase();
        return desc.contains("不满足") || desc.contains("不是") ||
            desc.contains("非") || desc.contains("错误的") ||
            desc.contains("异常的") || desc.contains("无效") ||
            desc.contains("不符");
    }

    /**
     * 对否定条件任务的提取结果进行后处理验证
     * 检测模型是否错误地返回了符合条件的内容
     */
    private Map<String, Object> validateNegativeConditionResult(String result, String reasoning,
                                                                  String labelDescription) {
        Map<String, Object> validation = new HashMap<>();
        validation.put("needs_correction", false);

        // 如果结果是"无"或"未提取"，则无需验证
        if (result == null || result.trim().isEmpty() ||
            result.equals("无") || result.equals("未提取到相关信息")) {
            return validation;
        }

        // 对于"不满足18位"类型的任务
        if (labelDescription.contains("18位")) {
            // 检查结果中是否包含18位号码
            Pattern digitPattern = Pattern.compile("\\d{18}");
            Matcher matcher = digitPattern.matcher(result);

            if (matcher.find()) {
                // 找到了18位号码，检查reasoning是否正确说明它应该被排除
                boolean reasoningSaysExclude = reasoning != null &&
                    (reasoning.contains("18位") || reasoning.contains("符合")) &&
                    (reasoning.contains("不满足") || reasoning.contains("未找到") ||
                     reasoning.contains("都是18位") || reasoning.contains("没有"));

                if (!reasoningSaysExclude) {
                    // 模型错误地返回了18位号码
                    validation.put("needs_correction", true);
                    validation.put("corrected_result", "无");
                    validation.put("correction_reason",
                        "检测到返回了18位号码，但任务要求提取不满足18位的号码，已自动修正为'无'");
                }
            }
        }

        // 检查reasoning中是否明确说"未找到"但result却有内容
        if (reasoning != null && reasoning.contains("未找到") && !result.equals("无")) {
            validation.put("needs_correction", true);
            validation.put("corrected_result", "无");
            validation.put("correction_reason", "推理说明未找到匹配内容，但结果有值，已修正为'无'");
        }

        return validation;
    }

    /**
     * 创建自由提取失败结果
     */
    private Map<String, Object> createFreeFormFailure(String errorMessage, long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("result", "提取失败: " + errorMessage);
        result.put("success", false);
        result.put("confidence", 0);
        result.put("error", errorMessage);
        result.put("durationMs", System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 从数据中提取指定字段的信息
     *
     * @param label 提取类型标签（包含 extractFields）
     * @param rowData 数据行
     * @param config 模型配置
     * @return 提取结果，包含 extractedData（Map）, confidence（Integer）, success（Boolean）
     */
    public Map<String, Object> extract(Label label, Map<String, Object> rowData, ModelConfigService.LLMRuntimeConfig config) {
        long startTime = System.currentTimeMillis();

        if (config == null) {
            config = modelConfigService.getDeepSeekRuntimeConfig();
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            return createExtractionFailure("API Key 未配置", startTime);
        }

        if (label.getExtractFields() == null || label.getExtractFields().isEmpty()) {
            return createExtractionFailure("提取字段未定义", startTime);
        }

        String prompt = buildExtractionPrompt(label, rowData);
        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", buildExtractionSystemPrompt(label.getExtractFields())));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        // 强制 JSON 输出格式
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());

        // 检测是否是千问的仅流式模型（如 qwq-32b），自动启用流式
        boolean requireStream = isQwenStreamOnlyModel(config.getModel());
        requestBody.put("stream", requireStream);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            return createExtractionFailure("请求JSON序列化失败: " + e.getMessage(), startTime);
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        if (code == 401 || code == 403) {
                            return createExtractionFailure("API认证失败: HTTP " + code, startTime);
                        }
                        if (code == 429) {
                            if (attempt >= maxRetry) {
                                return createExtractionFailure("大模型调用频率超限", startTime);
                            }
                            long delay = 10000L; // 10秒延迟
                            sleepBackoffWithDelay(delay);
                            continue;
                        }
                        if (attempt >= maxRetry) {
                            return createExtractionFailure("大模型调用失败: HTTP " + code, startTime);
                        }
                        sleepBackoff(attempt);
                        continue;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    return parseExtractionResult(body, label.getExtractFields(), startTime);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    return createExtractionFailure("请求超时", startTime);
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    log.error("网络通信失败: {}", e.getMessage(), e);
                    return createExtractionFailure("网络通信失败: " + e.getMessage(), startTime);
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("数据提取未知异常: {}", e.getMessage(), e);
                    return createExtractionFailure("调用异常: " + e.getMessage(), startTime);
                }
                sleepBackoff(attempt);
            }
        }

        return createExtractionFailure("重试次数耗尽", startTime);
    }

    /**
     * 构建提取类型的系统提示词
     */
    private String buildExtractionSystemPrompt(List<String> extractFields) {
        // 使用数据库中的默认提取提示词（按类型查询）
        String template = getSystemPromptByType(com.datalabeling.entity.SystemPrompt.PromptType.EXTRACTION);
        // 如果模板中需要字段列表，这里可以进一步处理
        // 当前数据库模板已包含 {{extract_fields}} 变量，会在后续渲染时替换
        return template;
    }

    /**
     * 构建提取类型的用户提示词
     */
    private String buildExtractionPrompt(Label label, Map<String, Object> rowData) {
        StringBuilder sb = new StringBuilder();
        sb.append("请从以下数据中提取指定字段的信息。\n\n");
        sb.append("提取说明: ").append(label.getDescription()).append("\n");
        
        // 根据是否有关注列，决定传递哪些数据
        Map<String, Object> dataToSend = filterDataByFocusColumns(label, rowData);
        
        if (label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
            sb.append("关注列: ").append(String.join(", ", label.getFocusColumns())).append("\n");
        }
        sb.append("\n数据行内容(JSON):\n");
        try {
            sb.append(objectMapper.writeValueAsString(dataToSend));
        } catch (Exception e) {
            sb.append(String.valueOf(dataToSend));
        }
        return sb.toString();
    }

    /**
     * 解析提取结果
     */
    private Map<String, Object> parseExtractionResult(String responseBody, List<String> extractFields, long startTime) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            // 尝试解析JSON
            String jsonContent = extractJson(content);
            if (jsonContent != null) {
                JsonNode resultNode = objectMapper.readTree(jsonContent);

                Map<String, Object> result = new HashMap<>();
                Map<String, Object> extractedData = new HashMap<>();

                // 提取各字段
                boolean hasAnyValue = false;
                for (String field : extractFields) {
                    JsonNode fieldNode = resultNode.path(field);
                    if (fieldNode.isNull() || fieldNode.isMissingNode()) {
                        extractedData.put(field, null);
                    } else {
                        String value = fieldNode.asText();
                        extractedData.put(field, value);
                        if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value)) {
                            hasAnyValue = true;
                        }
                    }
                }

                // 提取信心度
                int confidence = resultNode.path("confidence").asInt(hasAnyValue ? 80 : 30);
                confidence = Math.min(100, Math.max(0, confidence));

                // 提取分析原因
                String reasoning = resultNode.path("reasoning").asText("");

                result.put("extractedData", extractedData);
                result.put("confidence", confidence);
                result.put("reasoning", reasoning);
                result.put("success", true);
                result.put("durationMs", System.currentTimeMillis() - startTime);

                // 生成摘要
                result.put("summary", generateExtractionSummary(extractedData));

                return result;
            }

            return createExtractionFailure("无法解析响应内容: " + content, startTime);

        } catch (Exception e) {
            log.warn("解析提取结果失败: {}", e.getMessage());
            return createExtractionFailure("解析响应失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 生成提取结果摘要（用于 result 字段）
     */
    private String generateExtractionSummary(Map<String, Object> extractedData) {
        if (extractedData == null || extractedData.isEmpty()) {
            return "提取失败";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Object> entry : extractedData.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty()) {
                if (count > 0) {
                    sb.append("; ");
                }
                sb.append(entry.getKey()).append(": ").append(entry.getValue());
                count++;
                if (count >= 3) {
                    // 最多显示3个字段
                    if (extractedData.size() > 3) {
                        sb.append("...");
                    }
                    break;
                }
            }
        }

        if (count == 0) {
            return "未提取到信息";
        }

        return sb.toString();
    }

    /**
     * 创建提取失败结果
     */
    private Map<String, Object> createExtractionFailure(String errorMessage, long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("extractedData", null);
        result.put("confidence", 0);
        result.put("success", false);
        result.put("error", errorMessage);
        result.put("summary", "提取失败");
        result.put("durationMs", System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 构建带信心度和推理过程的系统提示词
     */
    private String buildSystemPromptWithConfidenceAndReasoning() {
        return getSystemPromptTemplate("classification_with_confidence");
    }

    /**
     * 解析带信心度和推理过程的响应
     */
    private Map<String, Object> parseReasoningResultWithConfidence(String body) {
        log.info("[parseReasoningResultWithConfidence] 开始解析LLM响应: body长度={}", body != null ? body.length() : 0);
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                log.info("[parseReasoningResultWithConfidence] 提取到content: 长度={}, 内容={}", content.length(), content);

                // 尝试解析 content 为 JSON
                String jsonContent = extractJson(content);
                log.info("[parseReasoningResultWithConfidence] extractJson结果: {}", jsonContent != null ? "提取到JSON" : "未提取到JSON");

                if (jsonContent != null) {
                    try {
                        JsonNode contentJson = objectMapper.readTree(jsonContent);
                        Map<String, Object> result = new HashMap<>();

                        // 解析结果
                        String resultValue = contentJson.path("result").asText("").trim();
                        result.put("result", normalizeResult(resultValue) != null ? normalizeResult(resultValue) : "否");

                        // 解析信心度
                        int confidence = contentJson.path("confidence").asInt(80);
                        result.put("confidence", Math.min(100, Math.max(0, confidence)));

                        // 解析推理过程
                        String reasoning = contentJson.path("reasoning").asText("").trim();
                        result.put("reasoning", reasoning);

                        log.info("[parseReasoningResultWithConfidence] JSON解析成功: result={}, confidence={}, reasoning长度={}, reasoning内容={}",
                            result.get("result"), result.get("confidence"), reasoning.length(), reasoning);

                        return result;
                    } catch (Exception e) {
                        log.warn("[parseReasoningResultWithConfidence] JSON解析失败，使用回退方案: error={}, content={}", e.getMessage(), content);
                    }
                }

                // 回退：尝试简单解析
                Map<String, Object> result = new HashMap<>();
                String parsedResult = parseYesNoContent(content);
                result.put("result", parsedResult);
                result.put("confidence", 80); // 默认信心度
                result.put("reasoning", content);

                log.info("[parseReasoningResultWithConfidence] 回退解析: result={}, confidence=80, reasoning长度={}, reasoning内容={}",
                    parsedResult, content.length(), content);

                return result;
            }

            // 返回默认值
            log.warn("[parseReasoningResultWithConfidence] 无法解析响应，返回默认值");
            Map<String, Object> defaultResult = new HashMap<>();
            defaultResult.put("result", "否");
            defaultResult.put("confidence", 0);
            defaultResult.put("reasoning", "无法解析响应");
            return defaultResult;
        } catch (Exception e) {
            log.error("[parseReasoningResultWithConfidence] 解析异常: error={}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("result", "否");
            errorResult.put("confidence", 0);
            errorResult.put("reasoning", "解析错误: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * @deprecated 使用 parseReasoningResultWithConfidence 代替
     */
    @Deprecated
    private Map<String, String> parseReasoningResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                // 尝试解析 content 为 JSON
                try {
                    JsonNode contentJson = objectMapper.readTree(content);
                    Map<String, String> result = new HashMap<>();
                    result.put("result", contentJson.path("result").asText("").trim());
                    result.put("reasoning", contentJson.path("reasoning").asText("").trim());
                    return result;
                } catch (Exception e) {
                    // 如果不是标准JSON，尝试简单的正则提取或直接返回
                    log.warn("Failed to parse reasoning JSON: {}", content);
                    Map<String, String> result = new HashMap<>();
                    result.put("result", parseYesNoContent(content)); // 复用现有的提取逻辑
                    result.put("reasoning", content);
                    return result;
                }
            }
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Parse error", e);
            return Collections.emptyMap();
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return DEFAULT_BASE_URL;
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private OkHttpClient getClient(Integer timeoutMs) {
        if (timeoutMs == null || timeoutMs <= 0) {
            return okHttpClient;
        }
        return clientByTimeout.computeIfAbsent(timeoutMs, ms -> okHttpClient.newBuilder()
            .connectTimeout(ms, TimeUnit.MILLISECONDS)
            .readTimeout(ms, TimeUnit.MILLISECONDS)
            .writeTimeout(ms, TimeUnit.MILLISECONDS)
            .build());
    }

    private Map<String, String> createMessage(String role, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("role", role);
        map.put("content", content);
        return map;
    }

    private String buildPrompt(Label label, Map<String, Object> rowData, String preprocessorResult) {
        log.info("[buildPrompt] 开始构建提示词: labelName={}, hasPreprocessorResult={}, includeInPrompt={}",
            label.getName(), preprocessorResult != null, label.shouldIncludePreprocessorInPrompt());

        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下规则判断这行数据是否符合标签定义。\n\n");
        sb.append("标签名称: ").append(label.getName()).append("\n");
        sb.append("标签规则: ").append(label.getDescription()).append("\n");

        // 根据是否有关注列，决定传递哪些数据
        Map<String, Object> dataToSend = filterDataByFocusColumns(label, rowData);

        if (label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
            sb.append("关注列: ").append(String.join(",", label.getFocusColumns())).append("\n");
        }
        sb.append("\n数据行内容(JSON):\n");
        try {
            sb.append(objectMapper.writeValueAsString(dataToSend));
        } catch (Exception e) {
            sb.append(String.valueOf(dataToSend));
        }

        // 如果有预处理结果且标签配置了要传入 LLM，则添加到提示词中
        if (preprocessorResult != null && !preprocessorResult.isEmpty() && label.shouldIncludePreprocessorInPrompt()) {
            sb.append("\n\n=== 规则引擎提取的证据 ===\n");
            sb.append(preprocessorResult);
            sb.append("\n\n=== 判断要求 ===\n");
            sb.append("上述证据由确定性规则引擎提取，具有高度可信度。\n");
            sb.append("请基于这些证据给出判断结论和理由，不要重新分析原始数据。\n");
            sb.append("你的任务是：理解规则提取的证据，并用简洁的语言总结判断依据。");
            log.info("[buildPrompt] 已添加规则引擎证据到提示词: 证据长度={}", preprocessorResult.length());
        }

        sb.append("\n\n请仅回答\"是\"或\"否\"，不要有任何额外解释。");

        log.info("[buildPrompt] 提示词构建完成: 总长度={}", sb.length());

        return sb.toString();
    }

    /**
     * 旧方法签名，保持兼容性
     */
    private String buildPrompt(Label label, Map<String, Object> rowData) {
        return buildPrompt(label, rowData, null);
    }

    /**
     * 构建带信心度的系统提示词
     */
    private String buildSystemPromptWithConfidence() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是专业的数据标注助手，请根据规则严格判断数据。\n\n");
        sb.append("请按以下JSON格式返回结果：\n");
        sb.append("{\n");
        sb.append("  \"result\": \"是\" 或 \"否\",\n");
        sb.append("  \"confidence\": 0-100的整数，表示你对判断结果的信心程度,\n");
        sb.append("  \"reason\": \"简短说明判断依据（不超过50字）\"\n");
        sb.append("}\n\n");
        sb.append("信心度说明：\n");
        sb.append("- 90-100: 非常确定，数据明确符合/不符合规则\n");
        sb.append("- 70-89: 比较确定，有较强的判断依据\n");
        sb.append("- 50-69: 一般确定，存在一些模糊之处\n");
        sb.append("- 0-49: 不太确定，数据信息不足或规则难以判断\n\n");
        sb.append("请只返回JSON，不要有其他内容。");
        return sb.toString();
    }

    /**
     * 构建带信心度的用户提示词
     */
    private String buildPromptWithConfidence(Label label, Map<String, Object> rowData, String preprocessorResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下规则判断这行数据是否符合标签定义。\n\n");
        sb.append("标签名称: ").append(label.getName()).append("\n");
        sb.append("标签规则: ").append(label.getDescription()).append("\n");

        // 根据是否有关注列，决定传递哪些数据
        Map<String, Object> dataToSend = filterDataByFocusColumns(label, rowData);

        if (label.getFocusColumns() != null && !label.getFocusColumns().isEmpty()) {
            sb.append("关注列: ").append(String.join(",", label.getFocusColumns())).append("\n");
        }
        sb.append("\n数据行内容(JSON):\n");
        try {
            sb.append(objectMapper.writeValueAsString(dataToSend));
        } catch (Exception e) {
            sb.append(String.valueOf(dataToSend));
        }

        // 如果有预处理结果且标签配置了要传入 LLM，则添加到提示词中
        if (preprocessorResult != null && !preprocessorResult.isEmpty() && label.shouldIncludePreprocessorInPrompt()) {
            sb.append("\n\n=== 规则引擎提取的证据 ===\n");
            sb.append(preprocessorResult);
            sb.append("\n\n=== 判断要求 ===\n");
            sb.append("上述证据由确定性规则引擎提取，具有高度可信度。\n");
            sb.append("请优先基于这些证据给出判断结论、置信度和理由。\n");
            sb.append("你的任务是：理解规则提取的证据，并用简洁的语言总结判断依据。");
        }

        return sb.toString();
    }

    /**
     * 解析带信心度的响应
     */
    private LabelJudgeResult parseResultWithConfidence(String responseBody, long durationMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            
            // 尝试解析JSON格式的响应
            String jsonContent = extractJson(content);
            if (jsonContent != null) {
                try {
                    JsonNode resultNode = objectMapper.readTree(jsonContent);
                    String result = resultNode.path("result").asText("");
                    int confidence = resultNode.path("confidence").asInt(80);
                    String reason = resultNode.path("reason").asText("");
                    
                    // 标准化结果
                    String normalizedResult = normalizeResult(result);
                    if (normalizedResult != null) {
                        return LabelJudgeResult.success(
                            normalizedResult,
                            BigDecimal.valueOf(Math.min(100, Math.max(0, confidence))),
                            reason,
                            durationMs
                        );
                    }
                } catch (Exception e) {
                    log.debug("JSON解析失败，尝试文本解析: {}", e.getMessage());
                }
            }
            
            // 回退到简单文本解析
            String normalized = content != null ? content.trim() : "";
            String cleaned = stripQuotes(normalized);
            String simpleResult = normalizeResult(cleaned);
            if (simpleResult != null) {
                // 简单回答默认给80%信心度
                return LabelJudgeResult.success(simpleResult, BigDecimal.valueOf(80), null, durationMs);
            }
            
            return LabelJudgeResult.failure("大模型返回内容无法解析: " + content, durationMs);
        } catch (Exception e) {
            log.warn("解析大模型响应失败: {}", e.getMessage());
            return LabelJudgeResult.failure("解析大模型响应失败: " + e.getMessage(), durationMs);
        }
    }

    /**
     * 从文本中提取JSON
     */
    private String extractJson(String text) {
        if (text == null) return null;
        
        // 尝试直接解析
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }
        
        // 尝试从markdown代码块中提取
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // 尝试找到JSON对象
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return null;
    }

    /**
     * 标准化结果为"是"或"否"
     */
    private String normalizeResult(String result) {
        if (result == null) return null;
        String cleaned = stripQuotes(result.trim());
        
        if ("是".equals(cleaned) || "yes".equalsIgnoreCase(cleaned) || "true".equalsIgnoreCase(cleaned)) {
            return "是";
        }
        if ("否".equals(cleaned) || "no".equalsIgnoreCase(cleaned) || "false".equalsIgnoreCase(cleaned)) {
            return "否";
        }
        
        // 处理带标点的情况
        if (cleaned.length() >= 1) {
            char first = cleaned.charAt(0);
            if (first == '是') return "是";
            if (first == '否') return "否";
        }
        
        return null;
    }

    private String parseYesNo(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            String normalized = content != null ? content.trim() : "";
            String cleaned = stripQuotes(normalized);
            if ("是".equals(cleaned) || "否".equals(cleaned)) {
                return cleaned;
            }
            if (cleaned.length() == 2) {
                char first = cleaned.charAt(0);
                char second = cleaned.charAt(1);
                if ((first == '是' || first == '否') && isAllowedTrailingPunctuation(second)) {
                    return String.valueOf(first);
                }
            }
            throw new BusinessException(ErrorCode.LLM_API_ERROR, "大模型返回内容无法解析为“是/否”");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析大模型响应失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.LLM_API_ERROR, "解析大模型响应失败");
        }
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim();
        // 去除成对包裹的引号
        while (s.length() >= 2 && isQuoteChar(s.charAt(0)) && isQuoteChar(s.charAt(s.length() - 1))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // 去除单侧引号
        while (!s.isEmpty() && isQuoteChar(s.charAt(0))) {
            s = s.substring(1).trim();
        }
        while (!s.isEmpty() && isQuoteChar(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private boolean isQuoteChar(char ch) {
        return ch == '"' || ch == '\'' || ch == '`' || ch == '“' || ch == '”' || ch == '‘' || ch == '’';
    }

    private boolean isAllowedTrailingPunctuation(char ch) {
        return ch == '。' || ch == '.'
            || ch == '！' || ch == '!'
            || ch == '？' || ch == '?'
            || ch == '，' || ch == ','
            || ch == '、'
            || ch == '；' || ch == ';';
    }

    private void sleepBackoff(int attempt) {
        try {
            long sleepMs = 500L * (1L << Math.min(attempt, 3));
            Thread.sleep(sleepMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取卡号类型的显示名称
     */
    private String getCardTypeDisplayName(String typeName) {
        if (typeName == null) {
            return "未知类型";
        }
        switch (typeName) {
            case "BANK_CARD":
                return "银行卡号";
            case "ID_CARD":
                return "身份证号";
            case "PHONE_NUMBER":
                return "手机号";
            case "SOCIAL_CARD":
                return "社保卡号";
            default:
                return typeName;
        }
    }

    /**
     * 将行数据转换为字符串
     */
    private String convertRowDataToString(Map<String, Object> rowData) {
        if (rowData == null || rowData.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            Object value = entry.getValue();
            if (value != null) {
                sb.append(value.toString());
            }
        }
        return sb.toString();
    }

    private String parseYesNoContent(String content) {
        if (content == null) return "否";
        content = content.trim().toLowerCase();
        if (content.contains("是") || content.contains("yes") || content.contains("true")) {
            return "是";
        }
        return "否";
    }

    /**
     * 调用强化分析
     * 用于二次验证和强化分析结果
     *
     * @param prompt 渲染后的强化提示词
     * @return LLM 响应的原始内容
     */
    public String callForEnhancement(String prompt) {
        ModelConfigService.LLMRuntimeConfig config = modelConfigService.getDeepSeekRuntimeConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.LLM_API_ERROR, "大模型 API Key 未配置");
        }

        String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getModel());

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(createMessage("system", "你是数据质量审核专家。请严格按照JSON格式返回分析结果。"));
        messages.add(createMessage("user", prompt));
        requestBody.put("messages", messages);

        requestBody.put("temperature", 0.1);  // 强化分析使用低温度
        requestBody.put("max_tokens", config.getMaxTokens());
        Map<String, String> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LLMSerializationException(
                ErrorCode.SYSTEM_ERROR,
                "请求JSON序列化失败: " + e.getMessage(),
                e,
                "REQUEST"
            );
        }

        int maxRetry = config.getRetryTimes() != null ? config.getRetryTimes() : 0;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .post(RequestBody.create(JSON, requestJson.getBytes(StandardCharsets.UTF_8)))
                    .build();

                try (ModelConcurrencyService.Permit ignored = modelConcurrencyService.acquire(config.getConfigId(), config.getMaxConcurrency());
                     Response response = getClient(config.getTimeout()).newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        int code = response.code();
                        // 记录响应体内容用于诊断
                        String errorBody = response.body() != null ? response.body().string() : "";
                        log.error("[DeepSeekService.judge] API调用失败: HTTP={}, url={}, model={}, response={}",
                            code, url, config.getModel(), errorBody);

                        if (code == 401 || code == 403) {
                            throw new LLMAuthenticationException(
                                ErrorCode.LLM_API_ERROR,
                                "API认证失败: HTTP " + code + ", 详情: " + errorBody
                            );
                        }
                        if (code == 429) {
                            throw new LLMRateLimitException(
                                ErrorCode.LLM_RATE_LIMIT,
                                "大模型调用频率超限"
                            );
                        }
                        throw new LLMCommunicationException(
                            ErrorCode.LLM_API_ERROR,
                            "大模型调用失败: HTTP " + code + ", 详情: " + errorBody,
                            code
                        );
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    return extractContent(body);
                }
            } catch (LLMRateLimitException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e.getRetryDelayMs() > 0 ? e.getRetryDelayMs() : getBackoffDelay(attempt);
                sleepBackoffWithDelay(delay);
            } catch (LLMAuthenticationException e) {
                // 认证错误不重试，直接抛出
                throw e;
            } catch (LLMCommunicationException | LLMTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw e;
                }
                long delay = e instanceof LLMTimeoutException ?
                    ((LLMTimeoutException) e).getRetryDelayMs() :
                    e.getRetryDelayMs();
                if (delay > 0) {
                    sleepBackoffWithDelay(delay);
                } else {
                    sleepBackoff(attempt);
                }
            } catch (SocketTimeoutException e) {
                if (attempt >= maxRetry) {
                    throw new LLMTimeoutException(
                        ErrorCode.LLM_API_ERROR,
                        "请求超时",
                        config.getTimeout() != null ? config.getTimeout().longValue() : -1L,
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (IOException e) {
                if (attempt >= maxRetry) {
                    throw new LLMCommunicationException(
                        ErrorCode.LLM_API_ERROR,
                        "网络通信失败: " + e.getMessage(),
                        e
                    );
                }
                sleepBackoff(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetry) {
                    log.error("强化分析调用未知异常: {}", e.getMessage(), e);
                    throw new BusinessException(ErrorCode.LLM_API_ERROR, "强化分析调用异常: " + e.getMessage());
                }
                sleepBackoff(attempt);
            }
        }

        throw new BusinessException(ErrorCode.LLM_API_ERROR, "强化分析调用失败");
    }

    /**
     * 从响应中提取内容
     */
    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("解析响应内容失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取默认自由提取提示词（针对 DeepSeek 70B 优化）
     */
    private String getDefaultFreeFormExtractionPrompt() {
        return "你是专业的数据提取助手。请根据用户给出的提取要求，从数据中提取信息。\n\n" +
               "请按以下JSON格式返回结果：\n" +
               "{\n" +
               "  \"result\": \"提取到的内容，如果没有符合条件的内容则返回'无'\",\n" +
               "  \"confidence\": 0-100的整数，表示提取结果的信心程度,\n" +
               "  \"reasoning\": \"简要说明提取依据和过程（不超过200字）\"\n" +
               "}\n\n" +
               "【重要】提取规则：\n" +
               "- 严格按照用户的提取要求进行匹配，不要进行语义扩展或模糊匹配\n" +
               "- 如果用户要求\"包含\"某个字符串，必须确保数据中【完全包含】该字符串（区分大小写），不能是相似词或近义词\n" +
               "- 如果用户要求\"查找\"或\"提取\"某个关键词，必须是【精确匹配】，不是语义相关\n" +
               "- 提取的内容必须是原始数据中的【原文内容】，不要编造、改写或推测\n" +
               "- reasoning 中必须说明：1)在哪个字段找到的 2)匹配的原文是什么 3)如果未找到，说明检查了哪些字段\n" +
               "- 请只返回JSON，不要有其他内容\n\n" +
               "【关键】否定条件处理规则：\n" +
               "- 如果提取要求包含\"不满足\"、\"不是\"、\"非\"、\"错误的\"、\"异常的\"等否定词\n" +
               "- 必须理解：这是要提取【不符合条件】的内容，而不是提取所有内容并说明是否符合\n" +
               "- 例如：\"提取不满足18位的身份证号\" = 只返回长度不是18位的号码，18位的不要返回\n" +
               "- 例如：\"提取错误的身份证号\" = 只返回格式错误的号码，正确的不要返回\n" +
               "- 如果所有内容都【符合条件】（即没有不符合条件的内容），result 必须返回\"无\"\n" +
               "- 【绝对禁止】当要求提取\"不符合条件\"的内容时，返回了符合条件的内容\n\n" +
               "【信心度标准】：\n" +
               "- 90-100: 找到完全匹配的内容\n" +
               "- 70-89: 找到部分匹配的内容\n" +
               "- 50-69: 找到可能相关但不确定的内容\n" +
               "- 0-49: 未找到匹配内容，result应为\"无\"";
    }

    /**
     * 获取默认结构化提取提示词
     */
    private String getDefaultExtractionPrompt() {
        return "你是专业的数据提取助手，请从给定的数据中提取指定字段的信息。\n\n" +
               "需要提取的字段：{{extract_fields}}\n\n" +
               "请按以下JSON格式返回结果：\n" +
               "{\n" +
               "{{#each extract_fields}}\n" +
               "  \"{{this}}\": \"提取的值或null\",\n" +
               "{{/each}}\n" +
               "  \"confidence\": 0-100的整数，表示对提取结果的信心程度,\n" +
               "  \"reasoning\": \"简要说明提取依据和过程（不超过100字）\"\n" +
               "}\n\n" +
               "【重要】提取规则：\n" +
               "- 严格按照字段名称进行精确匹配，不要进行语义扩展\n" +
               "- 如果某字段在数据中找不到【完全对应】的信息，必须返回 null\n" +
               "- 提取的值必须是原始数据中的【原文内容】，不要编造、改写或推测\n" +
               "- 不要将相似但不完全匹配的内容作为提取结果\n" +
               "- reasoning 中必须说明：从哪个字段提取、原文是什么、如果为null说明原因\n" +
               "- 请只返回JSON，不要有其他内容\n\n" +
               "【信心度标准】：\n" +
               "- 90-100: 找到完全匹配的字段和值\n" +
               "- 70-89: 找到高度相关的内容\n" +
               "- 50-69: 找到可能相关但不确定的内容\n" +
               "- 0-49: 未找到匹配内容，对应字段应为null\n\n" +
               "=== 提取说明 ===\n" +
               "{{label_description}}\n\n" +
               "{{#if focus_columns}}\n" +
               "=== 关注列 ===\n" +
               "{{focus_columns}}\n" +
               "{{/if}}\n\n" +
               "=== 原始数据 ===\n" +
               "{{row_data_json}}";
    }

    /**
     * 获取带信心度的分类提示词
     */
    private String getClassificationWithConfidencePrompt() {
        return "你是专业的数据标注助手，请根据规则严格判断数据。\n\n" +
               "请按以下JSON格式返回结果：\n" +
               "{\n" +
               "  \"result\": \"是\" 或 \"否\",\n" +
               "  \"confidence\": 0-100的整数，表示你对判断结果的信心程度,\n" +
               "  \"reasoning\": \"简短说明判断依据（不超过100字）\"\n" +
               "}\n\n" +
               "信心度说明：\n" +
               "- 90-100: 非常确定，数据明确符合/不符合规则\n" +
               "- 70-89: 比较确定，有较强的判断依据\n" +
               "- 50-69: 一般确定，存在一些模糊之处\n" +
               "- 0-49: 不太确定，数据信息不足或规则难以判断\n\n" +
               "请只返回JSON，不要有其他内容。\n\n" +
               "=== 原始数据 ===\n" +
               "{{row_data_json}}\n\n" +
               "{{#if preprocessor_result}}\n" +
               "=== 规则预处理结果 ===\n" +
               "{{preprocessor_result}}\n" +
               "{{/if}}\n\n" +
               "{{#if extracted_numbers}}\n" +
               "=== 提取到的号码 ===\n" +
               "{{extracted_numbers}}\n" +
               "{{/if}}";
    }

    /**
     * 检测是否是千问仅流式模型
     * qwq-32b 等模型只支持流式响应
     */
    private boolean isQwenStreamOnlyModel(String model) {
        if (model == null) {
            return false;
        }
        String modelLower = model.toLowerCase();
        return modelLower.contains("qwq") || modelLower.contains("qwen-reasoning");
    }

    /**
     * JDK 8 兼容的字符串重复方法
     */
    private String repeatString(String str, int count) {
        if (str == null || str.isEmpty() || count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 缩写字符串到指定长度
     */
    private String abbreviate(String str, int maxWidth) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxWidth) {
            return str;
        }
        return str.substring(0, maxWidth - 3) + "...";
    }

    /**
     * 将 Map 缩写为字符串（用于日志）
     */
    private String abbreviateMap(Map<String, Object> map, int maxEntries) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (count >= maxEntries) {
                sb.append("...");
                break;
            }
            if (count > 0) {
                sb.append(", ");
            }
            String value = String.valueOf(entry.getValue());
            if (value.length() > 50) {
                value = abbreviate(value, 50);
            }
            sb.append(entry.getKey()).append("=").append(value);
            count++;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 从 SSE 流式响应中提取完整的单次响应
     * 用于处理千问 qwq-32b 等仅支持流式的模型
     *
     * @param streamBody SSE 格式的流式响应体
     * @return 提取的完整响应 JSON（包含 choices[0].message.content）
     */
    private String extractCompleteResponseFromStream(String streamBody) {
        if (streamBody == null || streamBody.isEmpty()) {
            return streamBody;
        }

        try {
            // SSE 格式：每行以 "data: " 开头
            String[] lines = streamBody.split("\n");
            StringBuilder lastContent = new StringBuilder();
            String fullResponseBuilder = null;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 跳过 [DONE] 标记
                if ("data: [DONE]".equals(line)) {
                    continue;
                }

                // 提取 JSON 内容
                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6).trim();

                    // 尝试解析 JSON
                    try {
                        JsonNode chunk = objectMapper.readTree(jsonStr);

                        // 检查是否有 choices
                        JsonNode choices = chunk.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            JsonNode contentNode = delta.path("content");

                            // 如果有 content 字段，累积内容
                            if (!contentNode.isMissingNode()) {
                                lastContent.append(contentNode.asText());
                            }

                            // 同时尝试获取完整的 message（某些流式响应在最后会包含完整消息）
                            JsonNode message = choices.get(0).path("message");
                            if (!message.isMissingNode() && !message.path("content").isMissingNode()) {
                                // 找到完整响应，构建标准格式
                                fullResponseBuilder = chunk.toString();
                            }

                            // 检查是否完成（finish_reason）
                            JsonNode finishReason = choices.get(0).path("finish_reason");
                            if (!finishReason.isMissingNode() && "stop".equals(finishReason.asText())) {
                                // 流结束，如果有累积内容，构建完整响应
                                if (lastContent.length() > 0 && fullResponseBuilder == null) {
                                    // 构建标准 OpenAI 格式响应
                                    String constructedResponse = String.format(
                                        "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"%s\"},\"finish_reason\":\"stop\"}]}",
                                        escapeJson(lastContent.toString())
                                    );
                                    return constructedResponse;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[extractCompleteResponseFromStream] 解析 SSE 块失败: {}", e.getMessage());
                    }
                }
            }

            // 如果找到了完整响应，优先返回
            if (fullResponseBuilder != null) {
                return fullResponseBuilder;
            }

            // 否则返回从累积内容构建的响应
            if (lastContent.length() > 0) {
                return String.format(
                    "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"%s\"},\"finish_reason\":\"stop\"}]}",
                    escapeJson(lastContent.toString())
                );
            }

            // 都没有，返回原始响应
            return streamBody;
        } catch (Exception e) {
            log.warn("[extractCompleteResponseFromStream] 处理流式响应失败: {}", e.getMessage());
            return streamBody;
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}

