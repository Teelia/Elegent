package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.LabelMapper;
import com.datalabeling.dto.request.CreateLabelRequest;
import com.datalabeling.dto.request.UpdateLabelRequest;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.TaskLabelRepository;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final LabelMapper labelMapper;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;
    private final UserRepository userRepository;

    /**
     * 创建标签
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO createLabel(CreateLabelRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 确定作用域，默认为全局
        String scope = request.getScope();
        if (scope == null || scope.isEmpty()) {
            scope = Label.Scope.GLOBAL;
        }

        // 系统内置全局标签：仅管理员允许创建，避免普通用户污染全局库
        if (Label.Scope.GLOBAL.equals(scope) && !securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可创建系统内置全局标签");
        }

        // 如果是数据集专属标签，验证 datasetId
        Integer datasetId = null;
        if (Label.Scope.DATASET.equals(scope)) {
            if (request.getDatasetId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "数据集专属标签必须指定数据集ID");
            }
            datasetId = request.getDatasetId();
        }

        // 检查标签名称是否已存在
        Integer maxVersion = labelRepository.findMaxVersionByUserIdAndName(userId, request.getName());
        if (maxVersion != null) {
            throw new BusinessException(ErrorCode.LABEL_NAME_EXIST, "标签名称已存在，请使用更新功能");
        }

        // 确定标签类型，默认为分类
        String type = request.getType();
        if (type == null || type.isEmpty()) {
            type = Label.Type.CLASSIFICATION;
        }

        // 提取类型标签：提取字段可选，不填则使用自由提取模式
        // （不再强制要求提取字段）

        // builtin_level：global 仅管理员可创建，默认标记为 system；其他作用域均为 custom。
        String builtinLevel = Label.Scope.GLOBAL.equals(scope)
            ? Label.BuiltinLevel.SYSTEM
            : Label.BuiltinLevel.CUSTOM;

        Label label = Label.builder()
            .userId(userId)
            .name(request.getName())
            .version(1)
            .scope(scope)
            .type(type)
            .datasetId(datasetId)
            .description(request.getDescription())
            .focusColumns(request.getFocusColumns())
            .extractFields(request.getExtractFields())
            .extractorConfig(request.getExtractorConfig())
            .preprocessingMode(request.getPreprocessingMode())
            .preprocessorConfig(request.getPreprocessorConfig())
            .includePreprocessorInPrompt(request.getIncludePreprocessorInPrompt())
            .enableEnhancement(request.getEnableEnhancement())
            .enhancementConfig(request.getEnhancementConfig())
            .isActive(true)
            .builtinLevel(builtinLevel)
            .build();

        label = labelRepository.save(label);
        log.info("标签创建成功: id={}, name={}, scope={}", label.getId(), label.getName(), label.getScope());

        return labelMapper.toVO(label);
    }

    /**
     * 获取标签列表（分页）
     */
    public PageResult<LabelVO> getLabelList(Pageable pageable, Integer queryUserId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean isAdmin = securityUtil.isAdmin();
        Integer userId = isAdmin ? queryUserId : currentUserId;

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("page", pageable != null ? pageable.getPageNumber() + 1 : null);
        auditDetails.put("size", pageable != null ? pageable.getPageSize() : null);
        auditService.recordAdminRead(queryUserId, "admin_list_labels", "label", null, auditDetails, httpRequest);

        Page<Label> page = userId != null
            ? labelRepository.findLatestByUserId(userId, pageable)
            : labelRepository.findLatestAll(pageable);
        List<LabelVO> voList = page.getContent().stream()
            .map(labelMapper::toVO)
            .collect(Collectors.toList());

        return PageResult.of(voList, page.getTotalElements(),
            page.getNumber() + 1, page.getSize());
    }

    /**
     * 获取所有激活的标签（最新版本）
     * @param queryUserId 用户ID（管理员可指定）
     * @param scope 作用域过滤：global（全局）, dataset（数据集专属）
     * @param datasetId 数据集ID（scope=dataset时必须）
     */
    public List<LabelVO> getActiveLabels(Integer queryUserId, String scope, Integer datasetId, HttpServletRequest httpRequest) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        boolean isAdmin = securityUtil.isAdmin();
        Integer userId = isAdmin ? (queryUserId != null ? queryUserId : currentUserId) : currentUserId;

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("scope", scope);
        auditDetails.put("datasetId", datasetId);
        auditService.recordAdminRead(userId, "admin_read_active_labels", "label", null, auditDetails, httpRequest);

        List<Label> labels;
        
        // 根据 scope 过滤
        if (Label.Scope.GLOBAL.equals(scope)) {
            // 系统内置全局标签：只返回管理员创建的 global（最新版本）
            List<Integer> adminUserIds = userRepository.findAdminUserIds();
            labels = adminUserIds.isEmpty()
                ? Collections.emptyList()
                : labelRepository.findBuiltinGlobalActiveLatest(adminUserIds);
        } else if (Label.Scope.DATASET.equals(scope) && datasetId != null) {
            // 只返回指定数据集的专属标签（限定 scope=dataset，且仅激活最新版本）
            labels = labelRepository.findDatasetActiveLatestByDatasetId(datasetId);
        } else {
            // 默认返回所有激活标签
            labels = labelRepository.findLatestActiveByUserId(userId);
        }
        
        return labels.stream()
            .map(labelMapper::toVO)
            .collect(Collectors.toList());
    }

    /**
     * 获取标签详情
     */
    public LabelVO getLabelById(Integer id, HttpServletRequest httpRequest) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        // 权限检查
        if (!securityUtil.hasPermission(label.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("labelId", id);
        auditService.recordAdminRead(label.getUserId(), "admin_read_label_detail", "label", id, auditDetails, httpRequest);

        return labelMapper.toVO(label);
    }

    /**
     * 更新标签（直接更新当前版本）
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO updateLabel(Integer id, UpdateLabelRequest request) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        // 系统内置全局标签：仅管理员可维护
        if (Label.Scope.GLOBAL.equals(label.getScope()) && !securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置全局标签仅管理员可维护");
        }

        // 权限检查
        if (!securityUtil.hasPermission(label.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 直接更新当前版本的字段
        label.setDescription(request.getDescription());
        label.setFocusColumns(request.getFocusColumns());
        label.setExtractFields(request.getExtractFields());
        label.setExtractorConfig(request.getExtractorConfig());
        label.setPreprocessingMode(request.getPreprocessingMode());
        label.setPreprocessorConfig(request.getPreprocessorConfig());
        label.setIncludePreprocessorInPrompt(request.getIncludePreprocessorInPrompt());
        label.setEnableEnhancement(request.getEnableEnhancement());
        label.setEnhancementConfig(request.getEnhancementConfig());

        label = labelRepository.save(label);
        log.info("标签更新成功: id={}, name={}, version={}",
            label.getId(), label.getName(), label.getVersion());

        return labelMapper.toVO(label);
    }

    /**
     * 删除标签
     */
    @Transactional(rollbackFor = Exception.class)
    public LabelVO deleteLabel(Integer id) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        // 系统内置全局标签：仅管理员可维护
        if (Label.Scope.GLOBAL.equals(label.getScope()) && !securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统内置全局标签仅管理员可维护");
        }

        // 权限检查
        if (!securityUtil.hasPermission(label.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查是否被使用（同名标签的任何版本被任务引用都禁止删除）
        List<Label> versions = labelRepository.findByUserIdAndNameOrderByVersionDesc(label.getUserId(), label.getName());
        List<Integer> versionIds = versions.stream().map(Label::getId).collect(Collectors.toList());
        long count = versionIds.isEmpty() ? 0 : taskLabelRepository.countByLabelIdIn(versionIds);
        if (count > 0) {
            throw new BusinessException(ErrorCode.LABEL_IN_USE,
                "标签正在被 " + count + " 个任务使用，无法删除");
        }

        LabelVO vo = labelMapper.toVO(label);
        labelRepository.deleteAll(versions);
        log.info("标签删除成功: id={}, name={}", id, label.getName());
        return vo;
    }

    /**
     * 获取标签版本历史（同一用户 + 同名标签）
     */
    public List<LabelVO> getLabelVersions(Integer id, HttpServletRequest httpRequest) {
        Label label = labelRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        if (!securityUtil.hasPermission(label.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        HashMap<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("labelId", id);
        auditService.recordAdminRead(label.getUserId(), "admin_read_label_versions", "label", id, auditDetails, httpRequest);

        List<Label> versions = labelRepository.findByUserIdAndNameOrderByVersionDesc(label.getUserId(), label.getName());
        return versions.stream().map(labelMapper::toVO).collect(Collectors.toList());
    }
}
