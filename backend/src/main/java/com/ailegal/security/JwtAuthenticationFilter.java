package com.ailegal.security;

import com.ailegal.entity.SysUser;
import com.ailegal.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Security-Polish-1：JWT 认证过滤器（实时账号状态校验）。
 *
 * JWT 签名合法且未过期只代表 token 本身有效；在写入 SecurityContext 之前，
 * 必须按 JWT subject（userId）主键查询 sys_user 当前状态：
 *   用户不存在 / status != 1（禁用）→ 不建立 Authentication。
 * 不写 SecurityContext = 受保护接口由既有 HttpStatusEntryPoint 统一返回 401，
 * 前端 axios 401 拦截器自动清 token 跳登录页——禁用账号的旧 JWT 立即失效。
 *
 * 边界：
 * - 每次携带有效 token 的请求增加一次按主键查询（无 Redis / blacklist / 缓存，
 *   本科项目单机 MySQL 可接受；匿名请求零额外查询）。
 * - DB 查询异常按 fail-closed 处理：不写认证（401），绝不因查询失败放行。
 * - 不读取 DB 的 role，JWT claims 与权限模型保持现状（角色不随本轮重构）。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final SysUserMapper sysUserMapper;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, SysUserMapper sysUserMapper) {
        this.jwtUtils = jwtUtils;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtUtils.validateToken(token)) {
            Claims claims = jwtUtils.parseToken(token);
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            if (!userStillActive(userId)) {
                // 账号不存在/被禁用：不建立 Authentication，受保护接口 401
                filterChain.doFilter(request, response);
                return;
            }

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority(role)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, username, authorities);
            // principal 保留 userId（getCurrentUserId 依赖），真实用户名放入 details 供 getCurrentUsername 读取
            authentication.setDetails(username);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /** 当前用户仍存在且 status=1 才放行认证；DB 异常 fail-closed（不认证） */
    private boolean userStillActive(String userId) {
        try {
            SysUser user = sysUserMapper.selectById(Long.valueOf(userId));
            return user != null && user.getStatus() != null && user.getStatus() == 1;
        } catch (Exception e) {
            logger.warn("JWT 账号状态查询失败，按未认证处理: " + e.getMessage());
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
