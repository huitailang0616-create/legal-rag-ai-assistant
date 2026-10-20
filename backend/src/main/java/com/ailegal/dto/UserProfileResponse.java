package com.ailegal.dto;

import com.ailegal.entity.SysUser;

import java.time.LocalDateTime;

public class UserProfileResponse {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;

    public static UserProfileResponse fromEntity(SysUser user) {
        UserProfileResponse resp = new UserProfileResponse();
        resp.id = user.getId();
        resp.username = user.getUsername();
        resp.realName = user.getRealName();
        resp.phone = user.getPhone();
        resp.email = user.getEmail();
        resp.role = user.getRole();
        resp.status = user.getStatus();
        resp.createdAt = user.getCreatedAt();
        return resp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
