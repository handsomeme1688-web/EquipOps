package com.zoee.equipops.auth.interceptor;

import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Spring Security 升级前的路径权限实现，仅保留用于对比，当前未在 WebMvcConfig 注册。
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {
    private final PermissionCheckService permissionCheckService;
    private static final Map<String, String> PATH_PERMISSION = Map.of(
            "POST /devices",                  "device:create",
            "PUT /devices",                   "device:update",
            "DELETE /devices",                "device:delete",
            "POST /roles",                    "system:role:manage",
            "POST /users",                    "system:user:manage"

    );
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String permcode = matchPermission(method, uri);
        if (permcode != null) {
            boolean hasPermission = permissionCheckService.hasPerm(UserContext.getUserId(), permcode);
            if (!hasPermission) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":10003,\"msg\":\"无权限\"}");
                return false;
            }
        }
        return true;
    }

    private String matchPermission(String method, String uri) {
        for(Map.Entry<String, String> entry:PATH_PERMISSION.entrySet()){
            String key=entry.getKey();
            String[] parts=key.split(" ",2);
            if(method.equals(parts[0]) && (uri.equals(parts[1]) || uri.startsWith(parts[1] + "/"))){
                return entry.getValue();
            }
        }
        return null;
    }
}
