package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.config.properties.JwtProperties;
import com.datalabeling.dto.mapper.UserMapper;
import com.datalabeling.dto.request.LoginRequest;
import com.datalabeling.dto.response.LoginResponse;
import com.datalabeling.dto.response.UserVO;
import com.datalabeling.entity.AuditLog;
import com.datalabeling.entity.User;
import com.datalabeling.exception.BusinessException;
import com.datalabeling.repository.AuditLogRepository;
import com.datalabeling.repository.UserRepository;
import com.datalabeling.security.JwtTokenProvider;
import com.datalabeling.util.IpUtil;
import com.datalabeling.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final SecurityUtil securityUtil;

    /**
     * 用户登录
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        try {
            // 执行认证
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // 查询用户信息
            User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

            // 生成Token
            String token = jwtTokenProvider.generateToken(
                user.getUsername(),
                user.getId(),
                user.getRole()
            );

            // 更新最后登录时间
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            // 记录审计日志
            recordAuditLog(user.getId(), "login", httpRequest);

            // 构造响应
            UserVO userVO = userMapper.toVO(user);
            return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpiration())
                .user(userVO)
                .build();

        } catch (AuthenticationException e) {
            log.warn("登录失败: {} - {}", request.getUsername(), e.getMessage());
            throw new BusinessException(ErrorCode.USERNAME_PASSWORD_ERROR);
        }
    }

    /**
     * 记录审计日志
     */
    private void recordAuditLog(Integer userId, String action, HttpServletRequest request) {
        AuditLog auditLog = AuditLog.builder()
            .userId(userId)
            .action(action)
            .ipAddress(IpUtil.getIpAddress(request))
            .userAgent(IpUtil.getUserAgent(request))
            .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * 获取当前用户信息
     */
    public UserVO me() {
        User user = securityUtil.getCurrentUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userMapper.toVO(user);
    }

    /**
     * 退出登录（JWT无状态，记录审计即可）
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(HttpServletRequest request) {
        Integer userId = securityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        recordAuditLog(userId, "logout", request);
    }
}
