package com.datalabeling.service;

import com.datalabeling.entity.AuditLog;
import com.datalabeling.repository.AuditLogRepository;
import com.datalabeling.util.IpUtil;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志服务
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtil securityUtil;

    /**
     * 记录审计日志（基于当前登录用户）
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(String action, String resourceType, Integer resourceId,
                       Map<String, Object> details, HttpServletRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        record(userId, action, resourceType, resourceId, details, request);
    }

    /**
     * 记录审计日志（指定用户）
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(Integer userId, String action, String resourceType, Integer resourceId,
                       Map<String, Object> details, HttpServletRequest request) {
        AuditLog auditLog = AuditLog.builder()
            .userId(userId)
            .action(action)
            .resourceType(resourceType)
            .resourceId(resourceId)
            .details(details)
            .ipAddress(request != null ? IpUtil.getIpAddress(request) : null)
            .userAgent(request != null ? IpUtil.getUserAgent(request) : null)
            .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * 管理员越权读取审计：仅当当前用户为管理员，且读取目标用户（targetUserId）不是自己时记录。
     * targetUserId 为 null 时，表示“读取全量/未指定用户”，同样视为越权读取并记录。
     */
    public void recordAdminRead(Integer targetUserId, String action, String resourceType, Integer resourceId,
                                Map<String, Object> details, HttpServletRequest request) {
        if (!securityUtil.isAdmin()) {
            return;
        }
        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId == null) {
            return;
        }
        if (targetUserId != null && targetUserId.equals(currentUserId)) {
            return;
        }

        Map<String, Object> merged = details != null ? new HashMap<>(details) : new HashMap<>();
        merged.put("targetUserId", targetUserId);
        record(action, resourceType, resourceId, merged, request);
    }
}

