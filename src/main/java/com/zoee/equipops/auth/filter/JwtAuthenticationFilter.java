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
import org.springframework.security.core.Authentication;
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


        // 5. 同时设 UserContext（兼容旧代码还没改完的地方）
        // 6. filterChain.doFilter(request, response) — 放行给下一个 Filter
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
            /**
             * Authentication 是什么？
             * Spring Security 做完认证后，把"当前用户是谁"存到一个地方：SecurityContextHolder.getContext()。
             * 后续的 @PreAuthorize 注解、权限校验，都从这里拿。
             *
             * 你存进去的东西是一个 Authentication 接口的实现类：
             * new UsernamePasswordAuthenticationToken(
             *     主体身份,     // principal：userId 或 UserDetails
             *     null,         // credentials：密码。JWT 场景不存密码
             *     权限列表      // authorities：用户有哪些权限（@PreAuthorize 用的）
             * );
             */
            // 构造 Authentication（权限先用空列表，@PreAuthorize 暂时不生效，后续补）
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            // 存入 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(auth);

            // 4. 兼容旧代码
            UserContext.setUserId(userId);
            UserContext.setDeptId(deptId);
            filterChain.doFilter(request, response);
        }catch (Exception e){
            UserContext.remove();
            filterChain.doFilter(request,response);
        }finally {
            UserContext.remove();
        }
    }
}
