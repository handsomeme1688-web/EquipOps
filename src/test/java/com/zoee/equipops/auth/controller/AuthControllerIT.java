package com.zoee.equipops.auth.controller;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.auth.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("认证接口集成测试")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    // ① 注册重复用户名 → 409

    @Test
    @DisplayName("同一用户名注册两次，第二次返回 409")
    void shouldReturn409WhenRegisterDuplicateUsername() throws Exception {
        String json = """
                {
                    "username": "dupUser",
                    "password": "123456",
                    "realName": "重复用户",
                    "deptId": 2
                }""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.code").value(30001));
    }

    // ② 登录密码错误 → 401

    @Test
    @DisplayName("已注册用户使用错误密码登录 → 401")
    void shouldReturn401WhenWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "pwdUser",
                                    "password": "correctPassword",
                                    "realName": "密码测试",
                                    "deptId": 2
                                }"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "pwdUser",
                                    "password": "wrongPassword"
                                }"""))
                .andExpect(status().is(401))
                .andExpect(jsonPath("$.code").value(30002));
    }

    // ③ 无 token → 401
    // TODO: MockMvc + @Transactional + Security 在 Boot 3.5.x 下 anonymous 请求未正确拦截
    // 真实 HTTP 环境（RANDOM_PORT）下 Security 配置正常工作

    @Test
//    @Disabled("MockMvc anonymous auth bypass — Security config verified in real server mode")
    @DisplayName("不带 Authorization 头访问受保护接口 → 401")
    void shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/devices/page"))
                .andExpect(status().is(401));
    }

    // ④ 带有效 token → 200

    @Test
    @DisplayName("带有效 token 访问 /auth/me → 200 且返回当前用户信息")
    void shouldReturn200WithValidToken() throws Exception {
        String token = "Bearer " + jwtUtil.generateJwt(
                Map.of("userId", 1L, "deptId", 1L));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.realName").value("系统管理员"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    // 5 非法token
    @Test
    @DisplayName("非法 Token 访问受保护接口返回 401")
    void shouldReturn401WhenTokenInvalid() throws Exception {
        String expiredToken = Jwts.builder()
                .setClaims(Map.of("userId", 1L, "deptId", 1L))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(SignatureAlgorithm.HS256, jwtUtil.getSecret())
                .compact();


        mockMvc.perform(get("/devices/page").header("Authorization",expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }
}
