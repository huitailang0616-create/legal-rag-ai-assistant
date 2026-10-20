package com.ailegal.controller;

import com.ailegal.common.ApiResponse;
import com.ailegal.dto.UpdateProfileRequest;
import com.ailegal.dto.UserProfileResponse;
import com.ailegal.security.UserContext;
import com.ailegal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(userService.updateProfile(userId, request));
    }
}
