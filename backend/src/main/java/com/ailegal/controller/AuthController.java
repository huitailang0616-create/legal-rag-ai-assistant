package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.LoginRequest;
import com.ailegal.dto.LoginResponse;
import com.ailegal.dto.RegisterRequest;
import com.ailegal.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        LoginResponse resp = userService.login(request, ip);
        return ApiResponse.success(resp);
    }
}
