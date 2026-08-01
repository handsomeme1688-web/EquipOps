package com.zoee.equipops.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // private final LoginInterceptor loginInterceptor;
    // private final PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Security 接管前：LoginInterceptor 解析 JWT，PermissionInterceptor 校验权限
        // 现在由 SecurityConfig + JwtAuthenticationFilter + @OpLog 替代
    }
}
