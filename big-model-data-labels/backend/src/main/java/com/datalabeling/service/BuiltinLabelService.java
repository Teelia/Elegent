package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.LabelMapper;
import com.datalabeling.dto.response.LabelVO;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内置全局标签服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuiltinLabelService {

    private final LabelRepository labelRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;
    private final LabelMapper labelMapper;

    /**
     * 获取内置全局标签（分页）
     */
    public PageResult<LabelVO> list(String builtinCategory, Pageable pageable, HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("builtinCategory", builtinCategory);
        auditDetails.put("page", pageable != null ? pageable.getPageNumber() + 1 : null);
        auditDetails.put("size", pageable != null ? pageable.getPageSize() : null);
        auditService.record("read_builtin_labels", "label", null, auditDetails, request);

        List<Integer> adminUserIds = userRepository.findAdminUserIds();
        if (adminUserIds == null || adminUserIds.isEmpty()) {
            return PageResult.of(new ArrayList<>(), 0L, 1, pageable.getPageSize());
        }

        Page<Label> page;
        if (builtinCategory != null && !builtinCategory.trim().isEmpty()) {
            page = labelRepository.findBuiltinGlobalActiveLatestByCategory(adminUserIds, builtinCategory.trim(), pageable);
        } else {
            page = labelRepository.findBuiltinGlobalActiveLatest(adminUserIds, pageable);
        }

        Page<LabelVO> mapped = page.map(labelMapper::toVO);
        return PageResult.fromPage(mapped);
    }

    /**
     * 获取内置标签分类列表（静态配置，可按需扩展）
     */
    public List<Map<String, Object>> listCategories(HttpServletRequest request) {
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        auditService.record("read_builtin_label_categories", "label", null, new HashMap<>(), request);

        List<Map<String, Object>> categories = new ArrayList<>();
        categories.add(category("person_info_integrity", "人员信息完整性"));
        categories.add(category("case_feature", "案件特征"));
        categories.add(category("data_quality", "信息质量"));
        categories.add(category("behavior_pattern", "行为模式"));
        return categories;
    }

    private Map<String, Object> category(String code, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }

    /**
     * 启用/禁用内置标签（仅管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void setActive(Integer labelId, boolean active, HttpServletRequest request) {
        if (!securityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可修改内置标签状态");
        }

        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LABEL_NOT_FOUND));

        if (!Objects.equals(Label.Scope.GLOBAL, label.getScope()) ||
            !Objects.equals(Label.BuiltinLevel.SYSTEM, label.getBuiltinLevel())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅允许修改系统内置全局标签");
        }

        label.setIsActive(active);
        labelRepository.save(label);

        Map<String, Object> details = new HashMap<>();
        details.put("active", active);
        details.put("labelName", label.getName());
        details.put("labelVersion", label.getVersion());
        auditService.record("set_builtin_label_active", "label", labelId, details, request);
    }
}
