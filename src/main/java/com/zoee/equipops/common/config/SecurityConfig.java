package com.zoee.equipops.common.config;

import com.zoee.equipops.auth.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                //防止坏人用你的身份在你不注意时提交表单。但这个攻击是针对浏览器 Cookie 存登录态的网站。
                // 此项目是前后端分离，用 JWT（token 放 header 里，不是 Cookie），CSRF 防护没用，关了。
                .csrf(csrf->csrf.disable())

                // 每次请求都带 JWT token，服务器不保存用户状态，验完 JWT 就忘。这就是 STATELESS——无状态。
                .sessionManagement(sm-> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login","/auth/register","/error").permitAll() // 白名单
                        .anyRequest().authenticated() // 除了上面两个路径的其他请求必须认证（带有效 JWT）
                )
                .exceptionHandling(eh->eh
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);// 401
                            response.setContentType("application/json;charset=utf-8");
                            try {
                                response.getWriter().write("{\"code\":10002,\"msg\":\"未认证\"}");
                            } catch (IOException ignored) {}
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
                            response.setContentType("application/json;charset=utf-8");
                            try {
                                response.getWriter().write("{\"code\":10003,\"msg\":\"无权限\"}");
                            } catch (IOException ignored) {}
                        })
                )


                // 禁用表单登录。Spring Security 默认给你一个 /login 页面，用户名密码表单提交。
                // 有 JWT 后不需要这个，关了。
                .formLogin(fl->fl.disable())

                // 把你上面的配置组装成一个 SecurityFilterChain 对象，注册到 Spring 容器。
                .httpBasic(hb->hb.disable());
        return httpSecurity.build();
    }
}
