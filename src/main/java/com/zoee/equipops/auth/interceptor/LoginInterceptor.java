package com.zoee.equipops.auth.interceptor;

import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.common.context.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserContext.remove();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if(!StringUtils.hasLength(header) || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":10002,\"msg\":\"未认证\"}");
            return false;
        }
        String token = header.substring(7); // 跳过 "Bearer " 这 7 个字符
        try{
            Claims claims = jwtUtil.parseJwt(token);
            Long userId = Long.valueOf(claims.get("userId").toString());
            Long deptId = Long.valueOf(claims.get("deptId").toString());
            UserContext.setUserId(userId);
            UserContext.setDeptId(deptId);
            return true;
        }catch (Exception e){
            response.setStatus(401);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":10002,\"msg\":\"未认证\"}");
            return false;
        }
    }
}
