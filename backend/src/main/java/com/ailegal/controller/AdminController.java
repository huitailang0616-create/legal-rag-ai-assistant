package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.AdminUserResponse;
import com.ailegal.dto.UpdateStatusRequest;
import com.ailegal.entity.SysUser;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.SysUserMapper;
import com.ailegal.security.UserContext;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SysUserMapper sysUserMapper;
    private final OperationLogService operationLogService;

    public AdminController(SysUserMapper sysUserMapper, OperationLogService operationLogService) {
        this.sysUserMapper = sysUserMapper;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        List<SysUser> users = sysUserMapper.selectList(null);
        List<AdminUserResponse> list = users.stream()
                .map(AdminUserResponse::fromEntity)
                .toList();
        return ApiResponse.success(list);
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateStatusRequest request,
                                          HttpServletRequest httpRequest) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        // 保护规则：ADMIN 不能禁用/修改自己
        if (id.equals(UserContext.getCurrentUserId())) {
            throw new BusinessException(400, "不能修改当前登录账号的状态");
        }
        // 保护规则：仅允许修改普通 USER，管理员账号状态不可在此修改
        if (!"USER".equals(user.getRole())) {
            throw new BusinessException(403, "不能修改管理员账号状态");
        }

        Integer oldStatus = user.getStatus();
        user.setStatus(request.getStatus());
        sysUserMapper.updateById(user);

        String result = "targetUserId=" + id + ",oldStatus=" + oldStatus + ",newStatus=" + request.getStatus();
        operationLogService.log(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentUsername(),
                "USER_STATUS_UPDATE",
                user.getUsername(),
                result,
                httpRequest.getRemoteAddr()
        );

        return ApiResponse.success("状态更新成功", null);
    }
}
