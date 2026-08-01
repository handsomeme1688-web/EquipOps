package com.zoee.equipops.auth.filter;

import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.common.context.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 关键变化：所有路径都走到 filterChain.doFilter(request, response)——不再手动写 JSON，不再手动 setStatus(401)。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 1. 从 Authorization header 拿 token
        String header = request.getHeader("Authorization");
        if(!StringUtils.hasLength(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request,response);
            return;
        }
        // 2. 解析 JWT → 拿到 userId 和 deptId
        String token = header.substring(7); // 跳过 "Bearer " 这 7 个字符
        try{
            Claims claims = jwtUtil.parseJwt(token);
            // token 解析成功，设 userId
            Long userId = Long.valueOf(claims.get("userId").toString());
            Long deptId = Long.valueOf(claims.get("deptId").toString());
            // 3. 构造 Authentication 对象
            // TODO 构造 Authentication（权限先用空列表，@PreAuthorize 暂时不生效，后续补）
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            // 存入 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(auth);
            // 4. 兼容旧代码
            UserContext.setUserId(userId);
            UserContext.setDeptId(deptId);
            filterChain.doFilter(request, response);
        }catch (Exception e){
            SecurityContextHolder.clearContext();
            UserContext.remove();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":10002,\"msg\":\"Token 无效或已过期\"}");
        }finally {
            UserContext.remove();
        }
    }
}
