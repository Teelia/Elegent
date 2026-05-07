package com.datalabeling.entity;

import com.datalabeling.converter.StringListConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 标签实体
 * 支持全局标签和数据集专属标签
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "labels",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_name_version", columnNames = {"user_id", "name", "version"})
    },
    indexes = {
        @Index(name = "idx_labels_user_id", columnList = "user_id"),
        @Index(name = "idx_labels_name", columnList = "name"),
        @Index(name = "idx_labels_user_name_version", columnList = "user_id, name, version"),
        @Index(name = "idx_labels_scope", columnList = "scope"),
        @Index(name = "idx_labels_dataset_id", columnList = "dataset_id")
    })
public class Label extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * 标签名称
     */
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称长度不能超过100")
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 版本号
     */
    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号必须大于0")
    @Column(nullable = false)
    private Integer version = 1;

    /**
     * 作用域：global（全局）, dataset（数据集专属）, task（任务临时）
     */
    @NotBlank(message = "作用域不能为空")
    @Size(max = 20, message = "作用域长度不能超过20")
    @Column(nullable = false, length = 20)
    private String scope = Scope.GLOBAL;

    /**
     * 关联数据集ID（仅scope=dataset时有效）
     */
    @Column(name = "dataset_id")
    private Integer datasetId;

    /**
     * 关联任务ID（仅scope=task时有效，表示任务临时标签）
     */
    @Column(name = "task_id")
    private Integer taskId;

    /**
     * 标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取)
     */
    @Size(max = 30, message = "标签类型长度不能超过30")
    @Column(nullable = false, length = 30)
    private String type = Type.CLASSIFICATION;

    /**
     * 标签描述
     */
    @NotBlank(message = "标签描述不能为空")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * 重点关注列
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "focus_columns", columnDefinition = "JSON")
    private List<String> focusColumns;

    /**
     * 提取字段列表（仅type=extraction或structured_extraction时有效）
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "extract_fields", columnDefinition = "JSON")
    private List<String> extractFields;

    /**
     * 提取器配置（仅type=structured_extraction时有效）
     * JSON格式，包含提取器类型和选项
     */
    @Column(name = "extractor_config", columnDefinition = "JSON")
    private String extractorConfig;

    /**
     * 预处理模式（适用于 classification 和 extraction 类型）
     * llm_only: 仅使用 LLM，不使用规则提取器
     * rule_only: 仅使用规则提取器，不调用 LLM
     * rule_then_llm: 先用规则提取器预处理，然后将结果传给 LLM
     */
    @Size(max = 20, message = "预处理模式长度不能超过20")
    @Column(name = "preprocessing_mode", length = 20)
    private String preprocessingMode = PreprocessingMode.LLM_ONLY;

    /**
     * 预处理器配置（JSON格式，当preprocessingMode=rule_only或rule_then_llm时有效）
     * 包含extractors列表和各提取器的选项
     */
    @Column(name = "preprocessor_config", columnDefinition = "JSON")
    private String preprocessorConfig;

    /**
     * 是否将预处理结果传入 LLM（仅 rule_then_llm 模式有效）
     * true: 预处理结果会作为上下文传给 LLM，帮助提高判断准确性
     * false: 预处理结果仅用于内部逻辑，不传给 LLM
     */
    @Column(name = "include_preprocessor_in_prompt")
    private Boolean includePreprocessorInPrompt = true;

    /**
     * 是否启用二次强化分析
     */
    @Column(name = "enable_enhancement")
    private Boolean enableEnhancement = false;

    /**
     * 强化分析配置（JSON格式，当enableEnhancement=true时有效）
     * 包含触发条件、提示词ID等配置
     */
    @Column(name = "enhancement_config", columnDefinition = "JSON")
    private String enhancementConfig;

    /**
     * 是否激活
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * 内置级别：system（系统内置）/ custom（用户自定义）
     * <p>说明：当前系统中 global 标签仅管理员可创建，通常应为 system。</p>
     */
    @NotBlank(message = "内置级别不能为空")
    @Size(max = 20, message = "内置级别长度不能超过20")
    @Column(name = "builtin_level", nullable = false, length = 20)
    @Builder.Default
    private String builtinLevel = BuiltinLevel.CUSTOM;

    /**
     * 内置分类（可用于前端筛选/分组）
     * 例如：person_info_integrity / case_feature / data_quality 等
     */
    @Size(max = 50, message = "内置分类长度不能超过50")
    @Column(name = "builtin_category", length = 50)
    private String builtinCategory;

    /**
     * 作用域常量
     */
    public static class Scope {
        public static final String GLOBAL = "global";
        public static final String DATASET = "dataset";
        public static final String TASK = "task";
    }

    /**
     * 标签类型常量
     */
    public static class Type {
        public static final String CLASSIFICATION = "classification";
        public static final String EXTRACTION = "extraction";
        public static final String STRUCTURED_EXTRACTION = "structured_extraction";
    }

    /**
     * 预处理模式常量
     */
    public static class PreprocessingMode {
        public static final String LLM_ONLY = "llm_only";           // 仅使用 LLM，不使用规则提取器
        public static final String RULE_ONLY = "rule_only";         // 仅使用规则提取器，不调用 LLM
        public static final String RULE_THEN_LLM = "rule_then_llm"; // 规则预处理 + LLM 判断

        // 兼容旧值
        @Deprecated
        public static final String NONE = "none";
        @Deprecated
        public static final String LLM = "llm";
        @Deprecated
        public static final String RULE = "rule";
        @Deprecated
        public static final String HYBRID = "hybrid";
    }

    /**
     * 内置级别常量
     */
    public static class BuiltinLevel {
        public static final String SYSTEM = "system";
        public static final String CUSTOM = "custom";
    }

    /**
     * 构建标签键（name_v版本）
     */
    public String getLabelKey() {
        return name + "_v" + version;
    }

    /**
     * 判断是否为全局标签
     */
    public boolean isGlobal() {
        return Scope.GLOBAL.equals(scope);
    }

    /**
     * 判断是否为数据集专属标签
     */
    public boolean isDatasetSpecific() {
        return Scope.DATASET.equals(scope);
    }

    /**
     * 判断是否为任务临时标签
     */
    public boolean isTaskSpecific() {
        return Scope.TASK.equals(scope);
    }

    /**
     * 将任务临时标签转换为全局标签
     */
    public void promoteToGlobal() {
        this.scope = Scope.GLOBAL;
        this.taskId = null;
    }

    /**
     * 判断是否为分类标签
     */
    public boolean isClassification() {
        return type == null || Type.CLASSIFICATION.equals(type);
    }

    /**
     * 判断是否为提取标签
     */
    public boolean isExtraction() {
        return Type.EXTRACTION.equals(type);
    }

    /**
     * 判断是否为结构化提取标签
     */
    public boolean isStructuredExtraction() {
        return Type.STRUCTURED_EXTRACTION.equals(type);
    }

    /**
     * 判断是否启用预处理（需要配置规则提取器）
     */
    public boolean isPreprocessingEnabled() {
        String mode = preprocessingMode;
        return PreprocessingMode.RULE_ONLY.equals(mode) ||
               PreprocessingMode.RULE_THEN_LLM.equals(mode) ||
               // 兼容旧值
               PreprocessingMode.RULE.equals(mode) ||
               PreprocessingMode.HYBRID.equals(mode);
    }

    /**
     * 判断是否使用纯规则模式（不调用LLM）
     */
    public boolean isRuleOnlyMode() {
        return PreprocessingMode.RULE_ONLY.equals(preprocessingMode) ||
               // 兼容旧值
               PreprocessingMode.RULE.equals(preprocessingMode);
    }

    /**
     * 判断是否使用混合模式（规则预处理 + LLM）
     */
    public boolean isHybridMode() {
        return PreprocessingMode.RULE_THEN_LLM.equals(preprocessingMode) ||
               // 兼容旧值
               PreprocessingMode.HYBRID.equals(preprocessingMode);
    }

    /**
     * 判断是否仅使用 LLM（不使用规则提取器）
     */
    public boolean isLlmOnlyMode() {
        return PreprocessingMode.LLM_ONLY.equals(preprocessingMode) ||
               // 兼容旧值
               PreprocessingMode.NONE.equals(preprocessingMode) ||
               PreprocessingMode.LLM.equals(preprocessingMode);
    }

    /**
     * 判断是否启用二次强化分析
     */
    public boolean isEnhancementEnabled() {
        return enableEnhancement != null && enableEnhancement;
    }

    /**
     * 判断是否应该将预处理结果传入 LLM
     */
    public boolean shouldIncludePreprocessorInPrompt() {
        return isHybridMode() &&
               includePreprocessorInPrompt != null &&
               includePreprocessorInPrompt;
    }
}
