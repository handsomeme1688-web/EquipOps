package com.zoee.equipops.device.controller;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备数据隔离与接口权限集成测试。
 *
 * <p>覆盖认证、接口权限和部门隔离的关键行为：
 * <ol>
 *   <li>无 token → 401</li>
 *   <li>有 token 无权限（普通员工想创建设备）→ 403</li>
 *   <li>A 部门员工查设备列表 → 只看到本部门设备</li>
 *   <li>A 部门员工按 ID 查 B 部门设备 → 404（防 ID 越权）</li>
 *   <li>管理员查全部 → 不受部门限制</li>
 * </ol>
 *
 * <p>种子数据对照：
 * <pre>
 *   zhangsan (id=2, deptId=2) → EMPLOYEE 角色，device:view 有，device:create 无
 *   admin    (id=1, deptId=1) → ADMIN 角色，system:role:manage 有
 * </pre>
 *
 * @author zoe
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("设备数据隔离集成测试")
class DeviceIsolationIT {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceService deviceService;

    private String zhangsanToken;
    private String adminToken;
    private Long dept2DeviceId;
    private Long dept3DeviceId;

    @BeforeEach
    void setUp() {
        zhangsanToken = "Bearer " + jwtUtil.generateJwt(
                Map.of("userId", 2L, "deptId", 2L));
        adminToken = "Bearer " + jwtUtil.generateJwt(
                Map.of("userId", 1L, "deptId", 1L));

        // ── 造测试数据：两个部门各一台设备 ──
        // 不用 DeviceServiceImpl.create()——它强制从 UserContext 取 deptId，
        // 这里直接调 save() 绕过业务层
        Device d2 = new Device();
        d2.setCode("ISO-D2-001");
        d2.setName("注塑机一号");
        d2.setModel("ZX-100");
        d2.setLocation("一车间A区");
        d2.setDeptId(2L);
        d2.setOwnerId(2L);
        d2.setStatus(DeviceStatus.NORMAL);
        deviceService.save(d2);
        dept2DeviceId = d2.getId();

        Device d3 = new Device();
        d3.setCode("ISO-D3-001");
        d3.setName("数控铣床一号");
        d3.setModel("CNC-500");
        d3.setLocation("二车间A区");
        d3.setDeptId(3L);
        d3.setOwnerId(3L);
        d3.setStatus(DeviceStatus.NORMAL);
        deviceService.save(d3);
        dept3DeviceId = d3.getId();
    }

    // ──────────────── ① 无 token → 401 ────────────────

    @Test
    @DisplayName("不带 Authorization 头请求设备列表 → 401")
    void shouldReturn401WhenNoToken() throws Exception {
        mockMvc.perform(get("/devices/page"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(10002));
    }

    // ──────────────── ② 有 token 无权限 → 403 ────────────────

    @Test
    @DisplayName("普通员工（无 device:create）尝试创建设备 → 403")
    void shouldReturn403WhenNoPermission() throws Exception {
        String body = """
                {
                    "ownerId": 2,
                    "code": "NEW-DEV-999",
                    "name": "越权创建的设备",
                    "model": "X-1",
                    "location": "某处"
                }""";

                mockMvc.perform(post("/devices")
                        .header("Authorization", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // ──────────────── ③ 跨部门列表隔离 ────────────────

    @Test
    @DisplayName("zhangsan（部门2）查设备列表 → 只看得到部门2的设备")
    void shouldOnlySeeOwnDeptDevices() throws Exception {
        mockMvc.perform(get("/devices/page")
                        .header("Authorization", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("ISO-D2-001"))
                .andExpect(jsonPath("$.data.records[0].deptId").value(2));
    }

    // ──────────────── ④ 按 ID 越权 → 404 ────────────────

    @Test
    @DisplayName("zhangsan 按 ID 查 dept3 设备 → 404（防 ID 越权）")
    void shouldReturn404WhenAccessOtherDeptDeviceById() throws Exception {
        mockMvc.perform(get("/devices/" + dept3DeviceId)
                        .header("Authorization", zhangsanToken))
                .andExpect(status().isNotFound());
    }

    // ──────────────── ⑤ 管理员查全部 ────────────────

    @Test
    @DisplayName("管理员查设备列表 → 不受部门限制，看到全部")
    void shouldAdminSeeAllDevices() throws Exception {
        mockMvc.perform(get("/devices/page")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[?(@.deptId == 2)]").exists())
                .andExpect(jsonPath("$.data.records[?(@.deptId == 3)]").exists());
    }
}
