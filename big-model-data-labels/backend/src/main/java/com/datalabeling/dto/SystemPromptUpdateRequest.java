package com.datalabeling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 更新系统提示词请求 DTO
 * 不包含 userId，由系统从会话中获取
 * 不包含 id，通过路径参数传递
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemPromptUpdateRequest {

    /**
     * 提示词名称
     */
    @NotBlank(message = "提示词名称不能为空")
    @Size(max = 100, message = "提示词名称长度不能超过100")
    private String name;

    /**
     * 提示词代码（系统引用标识）
     */
    @NotBlank(message = "提示词代码不能为空")
    @Size(max = 50, message = "提示词代码长度不能超过50")
    private String code;

    /**
     * 提示词类型
     */
    @NotBlank(message = "提示词类型不能为空")
    @Size(max = 20, message = "提示词类型长度不能超过20")
    private String promptType;

    /**
     * 提示词模板（支持变量插值）
     */
    @NotBlank(message = "提示词模板不能为空")
    private String template;

    /**
     * 变量定义（JSON格式）
     */
    private List<String> variables;

    /**
     * 是否系统默认模板
     */
    private Boolean isSystemDefault;

    /**
     * 是否启用
     */
    private Boolean isActive;
}
