package com.zoee.equipops.auth.controller;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 11 认证接口集成测试。
 *
 * <p>覆盖手册要求的 4 条基础认证测试：
 * <ol>
 *   <li>注册重复用户名 → 409</li>
 *   <li>登录密码错误 → 401</li>
 *   <li>无 token 访问受保护接口 → 401</li>
 *   <li>带有效 token 访问 → 200</li>
 * </ol>
 *
 * @author zoe
 * @since 2026-07-25 Day 11
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("认证接口集成测试（Day 11）")
class AuthControllerIT {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // ──────────────── ① 注册重复用户名 → 409 ────────────────

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

        // 第一次：成功
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // 第二次：用户名已存在
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(409))
                .andExpect(jsonPath("$.code").value(30001));
    }

    // ──────────────── ② 登录密码错误 → 401 ────────────────

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

    // ──────────────── ③ 无 token → 401 ────────────────

    @Test
    @DisplayName("不带 Authorization 头访问受保护接口 → 401")
    void shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/devices/page"))
                .andExpect(status().is(401));
    }

    // ──────────────── ④ 带有效 token → 200 ────────────────

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
                .andExpect(jsonPath("$.data.deptName").value("总公司"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }
}
