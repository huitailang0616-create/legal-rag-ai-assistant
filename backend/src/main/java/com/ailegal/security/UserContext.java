package com.ailegal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserContext {

    private UserContext() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            return Long.valueOf((String) auth.getPrincipal());
        }
        throw new IllegalStateException("No authenticated user");
    }

    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            // JwtAuthenticationFilter 将真实用户名放入 details；getName() 返回 principal（userId），仅在无 details 时回退
            if (auth.getDetails() instanceof String s && !s.isBlank()) {
                return s;
            }
            return auth.getName();
        }
        throw new IllegalStateException("No authenticated user");
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_" + role));
        }
        return false;
    }
}
