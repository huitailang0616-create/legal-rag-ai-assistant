package com.ailegal.dto;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(max = 64, message = "姓名最长64位")
    private String realName;

    @Size(max = 20, message = "手机号最长20位")
    private String phone;

    @Size(max = 128, message = "邮箱最长128位")
    private String email;

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
