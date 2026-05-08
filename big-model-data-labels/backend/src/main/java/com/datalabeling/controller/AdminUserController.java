package com.datalabeling.controller;

import com.datalabeling.common.ApiResponse;
import com.datalabeling.common.PageResult;
import com.datalabeling.dto.request.CreateUserRequest;
import com.datalabeling.dto.request.ResetPasswordRequest;
import com.datalabeling.dto.request.UpdateUserRequest;
import com.datalabeling.dto.response.UserVO;
import com.datalabeling.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 管理员用户管理控制器
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminUserService.listUsers(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<UserVO> create(@Validated @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.success("创建成功", adminUserService.createUser(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserVO> update(@PathVariable("id") Integer id,
                                      @Validated @RequestBody UpdateUserRequest request,
                                      HttpServletRequest httpRequest) {
        return ApiResponse.success("更新成功", adminUserService.updateUser(id, request, httpRequest));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable("id") Integer id,
                                           @Validated @RequestBody ResetPasswordRequest request,
                                           HttpServletRequest httpRequest) {
        adminUserService.resetPassword(id, request, httpRequest);
        return ApiResponse.success("密码已重置", null);
    }
}

