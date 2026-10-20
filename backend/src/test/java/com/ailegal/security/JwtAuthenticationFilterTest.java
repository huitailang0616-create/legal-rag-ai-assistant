package com.ailegal.security;

import com.ailegal.entity.SysUser;
import com.ailegal.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security-Polish-1 自动化测试：JWT 实时账号状态校验（纯单元，Mockito + 真实 JwtUtils 固定 secret）。
 *
 *  A 合法 JWT + status=1        → Authentication 建立
 *  B 合法 JWT + status=0（禁用） → Authentication 不建立（受保护接口 401）
 *  C JWT userId 对应用户不存在   → Authentication 不建立
 *  D status 为 null（防御）      → Authentication 不建立
 *    注：sys_user 无 deleted 字段（schema.sql 仅 status，与业务表逻辑删除不同），
 *    不存在"逻辑删除用户"状态；记录消失即 C 场景。
 *  E1 无 token（public 路径）    → 不查询 DB、正常放行（permitAll 零 DB 开销）
 *  E2 DB 状态查询异常            → fail-closed 不认证（绝不因查询失败放行）
 *  F 正常 ADMIN JWT（status=1）  → Authentication 建立且含 ROLE_ADMIN（不受影响）
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    /** 固定测试 secret（HS256 要求 >= 32 字节） */
    private static final String SECRET = "SecurityPolish1TestSecret0123456789ABCDEF";
    private static final long EXPIRATION_MS = 86_400_000L;

    private final JwtUtils jwtUtils = new JwtUtils(SECRET, EXPIRATION_MS);

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtils, sysUserMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ----

    private String token(Long userId, String username, String role) {
        return jwtUtils.generateToken(userId, username, role);
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        return req;
    }

    private static SysUser user(Integer status) {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setStatus(status);
        u.setRole("USER");
        return u;
    }

    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    // ---- A ----

    @Test
    @DisplayName("A: 合法 JWT + status=1 → Authentication 建立（principal=userId, details=username, ROLE_USER）")
    void activeUserAuthenticationEstablished() throws Exception {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1));

        filter.doFilter(request(token(1L, "user1", "USER")), new MockHttpServletResponse(), filterChain);

        Authentication auth = currentAuth();
        assertNotNull(auth, "status=1 应建立 Authentication");
        assertEquals("1", auth.getPrincipal());
        assertEquals("user1", auth.getDetails());
        assertTrue(hasAuthority(auth, "ROLE_USER"));
        verify(sysUserMapper).selectById(1L);
    }

    // ---- B ----

    @Test
    @DisplayName("B: 合法 JWT + status=0（已禁用）→ Authentication 不建立")
    void disabledUserAuthenticationNotEstablished() throws Exception {
        when(sysUserMapper.selectById(1L)).thenReturn(user(0));

        filter.doFilter(request(token(1L, "user1", "USER")), new MockHttpServletResponse(), filterChain);

        assertNull(currentAuth(), "status=0 不得建立 Authentication");
        verify(filterChain).doFilter(any(), any());
    }

    // ---- C ----

    @Test
    @DisplayName("C: JWT userId 对应用户不存在（selectById=null）→ Authentication 不建立")
    void missingUserAuthenticationNotEstablished() throws Exception {
        when(sysUserMapper.selectById(1L)).thenReturn(null);

        filter.doFilter(request(token(1L, "user1", "USER")), new MockHttpServletResponse(), filterChain);

        assertNull(currentAuth(), "用户不存在不得建立 Authentication");
        verify(filterChain).doFilter(any(), any());
    }

    // ---- D ----

    @Test
    @DisplayName("D: status 为 null（防御，兼容登录处同型判断）→ Authentication 不建立")
    void nullStatusAuthenticationNotEstablished() throws Exception {
        when(sysUserMapper.selectById(1L)).thenReturn(user(null));

        filter.doFilter(request(token(1L, "user1", "USER")), new MockHttpServletResponse(), filterChain);

        assertNull(currentAuth(), "status=null 不得建立 Authentication");
        verify(filterChain).doFilter(any(), any());
    }

    // ---- E ----

    @Test
    @DisplayName("E1: 无 token（public/permitAll 路径）→ 不查询 DB、正常放行")
    void anonymousRequestNoDbQuery() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), filterChain);

        assertNull(currentAuth());
        verifyNoInteractions(sysUserMapper);
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    @DisplayName("E2: DB 状态查询异常 → fail-closed 不认证（绝不因查询失败放行）")
    void dbErrorFailClosed() throws Exception {
        when(sysUserMapper.selectById(1L)).thenThrow(new RuntimeException("db down"));

        filter.doFilter(request(token(1L, "user1", "USER")), new MockHttpServletResponse(), filterChain);

        assertNull(currentAuth(), "DB 异常必须 fail-closed 不认证");
        verify(filterChain).doFilter(any(), any());
    }

    // ---- F ----

    @Test
    @DisplayName("F: 正常 ADMIN JWT（status=1）→ Authentication 建立且含 ROLE_ADMIN")
    void adminJwtKeepsWorking() throws Exception {
        SysUser admin = user(1);
        admin.setId(2L);
        admin.setRole("ADMIN");
        when(sysUserMapper.selectById(2L)).thenReturn(admin);

        filter.doFilter(request(token(2L, "admin", "ADMIN")), new MockHttpServletResponse(), filterChain);

        Authentication auth = currentAuth();
        assertNotNull(auth, "正常 ADMIN 应建立 Authentication");
        assertTrue(hasAuthority(auth, "ROLE_ADMIN"));
    }
}
