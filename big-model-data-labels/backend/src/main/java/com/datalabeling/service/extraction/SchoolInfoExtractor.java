package com.datalabeling.service.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学校信息提取器
 *
 * <p>用途：为“在校学生”类判断提供规则证据（学校类型/学校名称/培训机构识别）。</p>
 *
 * <p>说明：该提取器为启发式规则，输出的是“证据片段”，最终结论仍应由 LLM 结合上下文判定。</p>
 *
 * <p>options（可选）：</p>
 * <ul>
 *   <li>exclude_training: true/false（默认 true）</li>
 * </ul>
 */
@Slf4j
@Component
public class SchoolInfoExtractor implements INumberExtractor {

    private static final Pattern TRAINING_KEYWORDS = Pattern.compile("培训|补习|辅导|教育培训|培训班|培训学校|培训机构|新东方|达内|中公教育|华图教育");

    private static final Pattern UNIVERSITY = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:大学|学院))");
    private static final Pattern VOCATIONAL = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:中职|中专|技校|职校|职业学院|职业学校))");
    private static final Pattern SENIOR_HIGH = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:高中|高级中学))");
    private static final Pattern JUNIOR_HIGH = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?(?:初中|初级中学|中学))");
    private static final Pattern PRIMARY = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?小学)");
    private static final Pattern KINDERGARTEN = Pattern.compile("([\\u4e00-\\u9fa5]{2,30}?幼儿园)");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        boolean excludeTraining = options == null || !Boolean.FALSE.equals(options.get("exclude_training"));

        // 1) 培训机构识别（高优先级）
        Matcher mTraining = TRAINING_KEYWORDS.matcher(text);
        if (mTraining.find()) {
            if (excludeTraining) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("机构类型", "培训机构");
                payload.put("命中关键词", mTraining.group());

                String json = toJson(payload);
                return Collections.singletonList(ExtractedNumber.builder()
                    .field("学校信息")
                    .value(json)
                    .confidence(0.85f)
                    .validation("识别为培训机构（不属于在校学生）")
                    .startIndex(mTraining.start())
                    .endIndex(mTraining.end())
                    .build());
            }
            // 不排除培训机构：继续向下提取其他学校信息
        }

        // 2) 学校类型/名称识别
        Set<String> names = new HashSet<>();
        String schoolType = null;
        List<String> validationSteps = new ArrayList<>();

        // 优先级：大学/学院 > 职业 > 高中 > 初中/中学 > 小学 > 幼儿园
        schoolType = collectFirstMatch(UNIVERSITY, text, names, schoolType, "university", validationSteps, "命中大学/学院关键词");
        schoolType = collectFirstMatch(VOCATIONAL, text, names, schoolType, "vocational", validationSteps, "命中中职/职校/技校关键词");
        schoolType = collectFirstMatch(SENIOR_HIGH, text, names, schoolType, "senior_high", validationSteps, "命中高中关键词");
        schoolType = collectFirstMatch(JUNIOR_HIGH, text, names, schoolType, "junior_high", validationSteps, "命中初中/中学关键词");
        schoolType = collectFirstMatch(PRIMARY, text, names, schoolType, "primary_school", validationSteps, "命中小学关键词");
        schoolType = collectFirstMatch(KINDERGARTEN, text, names, schoolType, "kindergarten", validationSteps, "命中幼儿园关键词");

        if (schoolType == null && names.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("学校类型", schoolType);
        if (!names.isEmpty()) {
            payload.put("学校名称", new ArrayList<>(names));
        }
        if (!validationSteps.isEmpty()) {
            payload.put("证据", validationSteps);
        }

        String json = toJson(payload);
        float confidence = schoolType != null ? 0.85f : 0.60f;

        return Collections.singletonList(ExtractedNumber.builder()
            .field("学校信息")
            .value(json)
            .confidence(confidence)
            .validation(String.join("; ", validationSteps))
            .build());
    }

    private String collectFirstMatch(Pattern pattern, String text, Set<String> names,
                                     String currentType, String candidateType,
                                     List<String> validationSteps, String evidence) {
        Matcher matcher = pattern.matcher(text);
        boolean matchedAny = false;
        while (matcher.find()) {
            matchedAny = true;
            String name = matcher.group(1);
            if (name != null) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        if (matchedAny) {
            validationSteps.add(evidence);
            if (currentType == null) {
                return candidateType;
            }
        }
        return currentType;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    @Override
    public String getExtractorType() {
        return "school_info";
    }
}

