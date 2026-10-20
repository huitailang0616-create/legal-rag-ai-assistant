package com.ailegal.service;

import com.ailegal.dto.*;

public interface UserService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String ip);

    UserProfileResponse getProfile(Long userId);

    UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request);
}
