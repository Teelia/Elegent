package com.datalabeling.service;

import com.datalabeling.dto.EnhancementConfig;
import com.datalabeling.entity.Label;
import com.datalabeling.util.PostProcessValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 回归测试：后置验证必须透传 labelName，确保“在校学生”走专用验证器（StudentInfoValidator）。
 */
@ExtendWith(MockitoExtension.class)
class EnhancementServicePostValidationTest {

    @Test
    void testPostValidation_ShouldPassLabelName() {
        SystemPromptService systemPromptService = mock(SystemPromptService.class);
        DeepSeekService deepSeekService = mock(DeepSeekService.class);
        PromptTemplateEngine promptTemplateEngine = mock(PromptTemplateEngine.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PostProcessValidator postProcessValidator = mock(PostProcessValidator.class);

        when(postProcessValidator.validate(any(), any(), any(), any()))
            .thenReturn(PostProcessValidator.ValidationResult.valid("ok"));

        EnhancementService svc = new EnhancementService(
            systemPromptService,
            deepSeekService,
            promptTemplateEngine,
            objectMapper,
            postProcessValidator
        );

        Label label = Label.builder()
            .name("在校学生信息完整性检查")
            .build();

        Map<String, Object> rowData = new HashMap<>();
        rowData.put("反馈内容", "学生 张三 16岁 身份证号：34040419971118021X 学校：安徽省XX中学 年级：初二 联系方式：13800138000");

        EnhancementConfig cfg = EnhancementConfig.builder()
            .triggerConfidence(70) // 让 shouldTrigger=false（initialConfidence=100）
            .build();

        svc.enhance(
            label,
            rowData,
            "否",
            100,
            "reasoning",
            "", // validationResult 为空，不触发强制强化
            cfg,
            Collections.emptyList()
        );

        verify(postProcessValidator, atLeastOnce())
            .validate(eq("否"), eq(rowData), eq(Collections.emptyList()), eq("在校学生信息完整性检查"));

        // shouldTrigger=false 时不应调用 LLM 强化
        verifyNoInteractions(systemPromptService);
        verifyNoInteractions(deepSeekService);
        verifyNoInteractions(promptTemplateEngine);
    }
}

