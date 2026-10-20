package com.ailegal.service.impl;

import com.ailegal.dto.*;
import com.ailegal.entity.SysUser;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.SysUserMapper;
import com.ailegal.security.JwtUtils;
import com.ailegal.service.OperationLogService;
import com.ailegal.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final OperationLogService operationLogService;

    public UserServiceImpl(SysUserMapper sysUserMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           OperationLogService operationLogService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getUsername, request.getUsername());
        if (sysUserMapper.selectCount(query) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName() != null ? request.getRealName() : "");
        user.setPhone(request.getPhone() != null ? request.getPhone() : "");
        user.setEmail(request.getEmail() != null ? request.getEmail() : "");
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.insert(user);

        operationLogService.log(user.getId(), user.getUsername(), "REGISTER", user.getUsername(), "注册成功", "");
    }

    @Override
    public LoginResponse login(LoginRequest request, String ip) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        query.eq(SysUser::getUsername, request.getUsername());
        SysUser user = sysUserMapper.selectOne(query);

        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
        operationLogService.log(user.getId(), user.getUsername(), "LOGIN", user.getUsername(), "登录成功", ip);

        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @Override
    public UserProfileResponse getProfile(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return UserProfileResponse.fromEntity(user);
    }

    @Override
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        user.setUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(user);

        return UserProfileResponse.fromEntity(user);
    }
}
