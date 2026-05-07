package com.datalabeling.entity;

import com.datalabeling.converter.StringListConverter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 系统提示词实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "system_prompts",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_prompts_code", columnNames = {"code"})
    },
    indexes = {
        @Index(name = "idx_system_prompts_user_type", columnList = "user_id, prompt_type"),
        @Index(name = "idx_system_prompts_type", columnList = "prompt_type"),
        @Index(name = "idx_system_prompts_code", columnList = "code")
    })
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemPrompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 所属用户ID（管理员创建的为全局共享）
     */
    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 提示词名称
     */
    @NotBlank(message = "提示词名称不能为空")
    @Size(max = 100, message = "提示词名称长度不能超过100")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 提示词代码（系统引用标识）
     */
    @NotBlank(message = "提示词代码不能为空")
    @Size(max = 50, message = "提示词代码长度不能超过50")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 提示词类型
     */
    @NotBlank(message = "提示词类型不能为空")
    @Size(max = 20, message = "提示词类型长度不能超过20")
    @Column(name = "prompt_type", nullable = false, length = 20)
    private String promptType;

    /**
     * 提示词模板（支持变量插值）
     */
    @NotBlank(message = "提示词模板不能为空")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    /**
     * 变量定义（JSON格式）
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "variables", columnDefinition = "JSON")
    private List<String> variables;

    /**
     * 是否系统默认模板
     */
    @Column(name = "is_system_default", nullable = false)
    @Builder.Default
    private Boolean isSystemDefault = false;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 提示词类型常量
     */
    public static class PromptType {
        public static final String CLASSIFICATION = "classification";    // 分类判断
        public static final String EXTRACTION = "extraction";            // LLM提取
        public static final String VALIDATION = "validation";            // 规则验证
        public static final String ENHANCEMENT = "enhancement";          // 二次强化
    }

    /**
     * 判断是否为分类提示词
     */
    public boolean isClassificationPrompt() {
        return PromptType.CLASSIFICATION.equals(promptType);
    }

    /**
     * 判断是否为提取提示词
     */
    public boolean isExtractionPrompt() {
        return PromptType.EXTRACTION.equals(promptType);
    }

    /**
     * 判断是否为强化提示词
     */
    public boolean isEnhancementPrompt() {
        return PromptType.ENHANCEMENT.equals(promptType);
    }

    /**
     * 判断是否为全局提示词（管理员创建）
     */
    public boolean isGlobal() {
        return isSystemDefault != null && isSystemDefault;
    }
}
