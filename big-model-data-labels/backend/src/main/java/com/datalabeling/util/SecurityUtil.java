package com.datalabeling.util;

import com.datalabeling.entity.User;
import com.datalabeling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 安全工具类
 */
@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final UserRepository userRepository;

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }

    /**
     * 获取当前登录用户实体
     */
    public User getCurrentUser() {
        String username = getCurrentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    /**
     * 获取当前登录用户ID
     */
    public Integer getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 检查当前用户是否为管理员
     */
    public boolean isAdmin() {
        User user = getCurrentUser();
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    /**
     * 检查当前用户是否有权限访问指定资源
     */
    public boolean hasPermission(Integer resourceUserId) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        // 管理员拥有所有权限
        if (isAdmin()) {
            return true;
        }
        // 普通用户只能访问自己的资源
        return currentUser.getId().equals(resourceUserId);
    }
}
