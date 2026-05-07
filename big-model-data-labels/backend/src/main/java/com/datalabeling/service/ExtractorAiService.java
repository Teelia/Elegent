package com.datalabeling.service;

import com.datalabeling.dto.request.AiGenerateExtractorRequest;
import com.datalabeling.dto.response.AiGenerateExtractorResponse;
import com.datalabeling.dto.response.AiGenerateExtractorResponse.PatternSuggestion;
import com.datalabeling.entity.ModelConfig;
import com.datalabeling.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 提取器AI辅助服务
 * 使用当前配置的大模型生成正则表达式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractorAiService {

    private final OkHttpClient httpClient;
    private final ModelConfigService modelConfigService;
    private final SyncCryptoService syncCryptoService;
    private final ModelConfigRepository modelConfigRepository;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * 使用AI生成提取器配置
     */
    public AiGenerateExtractorResponse generateExtractor(AiGenerateExtractorRequest request) {
        String prompt = buildPrompt(request);

        try {
            String jsonResponse = callDeepSeekApi(prompt);
            return parseResponse(jsonResponse, request.getExtractorName());
        } catch (Exception e) {
            log.error("AI生成提取器失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建AI提示词
     */
    private String buildPrompt(AiGenerateExtractorRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个正则表达式专家。请根据以下需求生成正则表达式提取规则。\n\n");

        if (AiGenerateExtractorRequest.Mode.DESCRIPTION.equals(request.getMode())) {
            prompt.append("## 描述式生成\n");
            prompt.append("提取器名称: ").append(request.getExtractorName()).append("\n");
            prompt.append("需求描述: ").append(request.getDescription()).append("\n");
        } else if (AiGenerateExtractorRequest.Mode.SAMPLES.equals(request.getMode())) {
            prompt.append("## 示例式生成\n");
            prompt.append("提取器名称: ").append(request.getExtractorName()).append("\n");
            prompt.append("示例数据（每行一个）:\n");
            prompt.append(request.getSamples()).append("\n");
        }

        prompt.append("\n## 要求\n");
        prompt.append("1. 生成的正则表达式要精确匹配目标数据\n");
        prompt.append("2. 避免过度匹配或匹配不足\n");
        prompt.append("3. 如果有多种格式（如18位和15位身份证），请提供多个规则\n");
        prompt.append("4. 每个规则提供匹配示例和不匹配示例\n");

        if (Boolean.TRUE.equals(request.getNeedValidation())) {
            prompt.append("5. 考虑是否需要校验位验证（如身份证、银行卡号）\n");
        }

        prompt.append("\n## 输出格式\n");
        prompt.append("请以JSON格式输出，结构如下：\n");
        prompt.append("{\n");
        prompt.append("  \"description\": \"提取器描述\",\n");
        prompt.append("  \"patterns\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"name\": \"规则名称\",\n");
        prompt.append("      \"pattern\": \"正则表达式\",\n");
        prompt.append("      \"description\": \"规则说明\",\n");
        prompt.append("      \"priority\": 100,\n");
        prompt.append("      \"confidence\": 0.95,\n");
        prompt.append("      \"validationType\": \"none/checksum/luhn\",\n");
        prompt.append("      \"example\": \"匹配示例\",\n");
        prompt.append("      \"negativeExample\": \"不匹配示例\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"explanation\": \"生成思路说明\"\n");
        prompt.append("}\n");

        prompt.append("\n请直接输出JSON，不要有任何额外说明文字。");

        return prompt.toString();
    }

    /**
     * 调用大模型 API（使用当前配置的模型）
     */
    private String callDeepSeekApi(String prompt) throws IOException {
        // 获取当前配置的默认模型
        ModelConfigService.LLMRuntimeConfig config = modelConfigService.getDeepSeekRuntimeConfig();
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IOException("大模型 API Key 未配置，请先在模型配置页面设置");
        }

        // 构建请求体
        String requestBody = String.format(
                "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.3,\"max_tokens\":2000}",
                config.getModel(),
                prompt.replace("\"", "\\\"").replace("\n", "\\n")
        );

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API调用失败: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JsonNode jsonNode = new ObjectMapper().readTree(responseBody);

            // 提取AI返回的内容
            String content = jsonNode.path("choices").path(0).path("message").path("content").asText();
            log.info("大模型 API返回: {}", content);

            // 提取JSON部分（有时AI会在JSON前后添加说明文字）
            return extractJson(content);
        }
    }

    /**
     * 从文本中提取JSON
     */
    private String extractJson(String text) {
        // 查找第一个 { 和最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return text;
    }

    /**
     * 解析AI响应
     */
    private AiGenerateExtractorResponse parseResponse(String jsonResponse, String extractorName) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);

        AiGenerateExtractorResponse response = new AiGenerateExtractorResponse();

        // 设置建议代码（基于名称转拼音或英文）
        response.setSuggestedCode(generateSuggestedCode(extractorName));

        // 设置描述
        response.setDescription(root.path("description").asText());

        // 解析规则列表
        List<PatternSuggestion> patterns = new ArrayList<>();
        JsonNode patternsNode = root.path("patterns");
        if (patternsNode.isArray()) {
            for (JsonNode patternNode : patternsNode) {
                PatternSuggestion suggestion = new PatternSuggestion();
                suggestion.setName(patternNode.path("name").asText());
                suggestion.setPattern(patternNode.path("pattern").asText());
                suggestion.setDescription(patternNode.path("description").asText());
                suggestion.setPriority(patternNode.path("priority").asInt(100));
                suggestion.setConfidence(patternNode.path("confidence").asDouble(0.9));
                suggestion.setValidationType(patternNode.path("validationType").asText("none"));
                suggestion.setExample(patternNode.path("example").asText());
                suggestion.setNegativeExample(patternNode.path("negativeExample").asText());
                patterns.add(suggestion);
            }
        }
        response.setPatterns(patterns);

        // 设置说明
        response.setExplanation(root.path("explanation").asText());

        log.info("AI生成提取器成功: {}, 规则数: {}", extractorName, patterns.size());

        return response;
    }

    /**
     * 生成建议的提取器代码
     * 简化处理：将中文转为英文或拼音（这里使用简化规则）
     */
    private String generateSuggestedCode(String name) {
        // 移除特殊字符，保留中文、字母、数字
        String code = name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");

        // 简单转拼音映射（常用词）
        code = code.replace("身份证", "id_card")
                .replace("手机", "mobile")
                .replace("电话", "phone")
                .replace("邮箱", "email")
                .replace("车牌", "license_plate")
                .replace("银行卡", "bank_card")
                .replace("身份证号", "id_card")
                .replace("号码", "")
                .replace("提取", "")
                .replace("器", "");

        // 转小写，用下划线分隔
        code = code.toLowerCase().replaceAll("\\s+", "_");

        if (code.isEmpty()) {
            code = "custom_extractor";
        }

        return code;
    }
}
