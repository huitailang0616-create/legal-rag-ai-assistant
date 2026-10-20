package com.ailegal.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Final-Requirement-Gaps-B 修复回归测试：
 * getCurrentUsername() 优先读 details（真实用户名），无 details 时回退 getName()；
 * getCurrentUserId() 仍解析 principal（userId），不受影响。
 */
class UserContextTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("details=username -> getCurrentUsername 返回真实用户名，getCurrentUserId 仍为 principal")
    void detailsProvided_returnsRealUsername_keepsUserId() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("42", "alice", List.of());
        auth.setDetails("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals("alice", UserContext.getCurrentUsername());
        assertEquals(42L, UserContext.getCurrentUserId());
    }

    @Test
    @DisplayName("无 details -> 回退 getName()（兼容旧构造路径）")
    void noDetails_fallsBackToName() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("42", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals("42", UserContext.getCurrentUsername());
    }

    @Test
    @DisplayName("blank details -> 同样回退 getName()")
    void blankDetails_fallsBackToName() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("42", "bob", List.of());
        auth.setDetails("   ");
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertEquals("42", UserContext.getCurrentUsername());
    }

    @Test
    @DisplayName("未认证 -> 抛 IllegalStateException")
    void noAuthentication_throws() {
        assertThrows(IllegalStateException.class, UserContext::getCurrentUsername);
        assertThrows(IllegalStateException.class, UserContext::getCurrentUserId);
    }
}
