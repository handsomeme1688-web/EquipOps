package com.zoee.equipops.auth.filter;

import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.system.service.PermissionService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析有效 JWT 后建立 SecurityContext；缺少 Token 的请求交给 Spring Security，
 * 携带无效 Token 的请求直接返回 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasLength(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        Claims claims;
        Long userId;
        Long deptId;
        try {
            claims = jwtUtil.parseJwt(token);
            userId = Long.valueOf(claims.get("userId").toString());
            deptId = Long.valueOf(claims.get("deptId").toString());
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            UserContext.remove();

            writeUnauthorized(response);
            return;
        }

        List<GrantedAuthority> authorities = permissionService.listPermissionCodesByUser(userId).stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserContext.setUserId(userId);
        UserContext.setDeptId(deptId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            UserContext.remove();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":10002,\"msg\":\"Token 无效或已过期\"}");
    }
}
