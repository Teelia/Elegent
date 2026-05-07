package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.mapper.UserMapper;
import com.datalabeling.dto.request.CreateUserRequest;
import com.datalabeling.dto.request.ResetPasswordRequest;
import com.datalabeling.dto.request.UpdateUserRequest;
import com.datalabeling.dto.response.UserVO;
import com.datalabeling.entity.User;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SecurityUtil securityUtil;
    private final AuditService auditService;

    public PageResult<UserVO> listUsers(Integer page, Integer size, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.search(keyword, pageable);

        List<UserVO> items = userPage.getContent().stream()
            .map(userMapper::toVO)
            .collect(Collectors.toList());

        return PageResult.of(items, userPage.getTotalElements(),
            userPage.getNumber() + 1, userPage.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(CreateUserRequest request, HttpServletRequest httpRequest) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        if (username.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名不能为空");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXIST);
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            if (userRepository.findByEmail(request.getEmail().trim()).isPresent()) {
                throw new BusinessException(ErrorCode.EMAIL_EXIST);
            }
        }

        String role = normalizeRole(request.getRole());

        User user = User.builder()
            .username(username)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(role)
            .email(trimToNull(request.getEmail()))
            .fullName(trimToNull(request.getFullName()))
            .isActive(Boolean.TRUE.equals(request.getIsActive()))
            .build();

        user = userRepository.save(user);
        log.info("管理员创建用户: id={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> details = new HashMap<>();
        details.put("targetUserId", user.getId());
        details.put("username", user.getUsername());
        details.put("role", user.getRole());
        details.put("isActive", user.getIsActive());
        auditService.record("admin_create_user", "user", user.getId(), details, httpRequest);

        return userMapper.toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Integer id, UpdateUserRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            // 避免管理员误操作导致无法登录/失去权限
            if (!Boolean.TRUE.equals(request.getIsActive())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许禁用当前登录账号");
            }
            String nextRole = normalizeRole(request.getRole());
            if (!"admin".equalsIgnoreCase(nextRole)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许修改当前登录账号的角色");
            }
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            User existing = userRepository.findByEmail(request.getEmail().trim()).orElse(null);
            if (existing != null && !existing.getId().equals(user.getId())) {
                throw new BusinessException(ErrorCode.EMAIL_EXIST);
            }
        }

        user.setRole(normalizeRole(request.getRole()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setFullName(trimToNull(request.getFullName()));
        user.setIsActive(Boolean.TRUE.equals(request.getIsActive()));

        user = userRepository.save(user);
        log.info("管理员更新用户: id={}, username={}, role={}, active={}",
            user.getId(), user.getUsername(), user.getRole(), user.getIsActive());

        Map<String, Object> details = new HashMap<>();
        details.put("targetUserId", user.getId());
        details.put("role", user.getRole());
        details.put("isActive", user.getIsActive());
        auditService.record("admin_update_user", "user", user.getId(), details, httpRequest);

        return userMapper.toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Integer id, ResetPasswordRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Integer currentUserId = securityUtil.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(user.getId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许通过管理员接口重置当前登录账号密码");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("管理员重置用户密码: id={}, username={}", user.getId(), user.getUsername());

        Map<String, Object> details = new HashMap<>();
        details.put("targetUserId", user.getId());
        details.put("username", user.getUsername());
        auditService.record("admin_reset_password", "user", user.getId(), details, httpRequest);
    }

    private String normalizeRole(String role) {
        String r = role != null ? role.trim().toLowerCase(Locale.ROOT) : "";
        if ("admin".equals(r) || "normal".equals(r)) {
            return r;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "角色仅支持 admin/normal");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
