package com.zoee.equipops.order.controller;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.service.RepairOrderService;
import com.zoee.equipops.system.entity.OperationLog;
import com.zoee.equipops.system.mapper.OperationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "equipops.order.timeout-scan.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DisplayName("工单创建、校验与幂等集成测试")
class RepairOrderControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private RepairOrderService repairOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private OperationLogMapper operationLogMapper;

    private Long deviceId;
    private String token;

    @BeforeEach
    void setUp() {
        Device device = new Device();
        device.setCode("ORDER-IT-" + UUID.randomUUID());
        device.setName("工单测试设备");
        device.setModel("TEST-M1");
        device.setLocation("第一车间");
        device.setDeptId(2L);
        device.setOwnerId(2L);
        device.setStatus(DeviceStatus.NORMAL);
        deviceService.save(device);
        deviceId = device.getId();
        token = "Bearer " + jwtUtil.generateJwt(Map.of("userId", 2L, "deptId", 2L));
    }

    @AfterEach
    void tearDown() {
        repairOrderService.remove(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getDeviceId, deviceId));
        deviceService.removeById(deviceId);
        Set<String> keys = stringRedisTemplate.keys("idempotency:order:create:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    void missingRequiredFieldsShouldReturn400AndFieldNames() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", "missing-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.data.deviceId").exists())
                .andExpect(jsonPath("$.data.description").exists())
                .andExpect(jsonPath("$.data.priority").exists());
    }

    @Test
    void illegalPriorityAndTooLongDescriptionShouldReturn400() throws Exception {
        String body = """
                {"deviceId": %d, "description": "%s", "priority": -1}
                """.formatted(deviceId, "x".repeat(256));

        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", "invalid-values")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.description").exists())
                .andExpect(jsonPath("$.data.priority").exists());
    }

    @Test
    void missingIdempotencyHeaderShouldReturn400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("缺请求头")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data['Idempotency-Key']").exists());
    }

    @Test
    void repeatedKeyShouldReturnOriginalOrderEvenAfterRedisKeyIsDeleted() throws Exception {
        String idempotencyKey = "same-key-" + UUID.randomUUID();
        String firstResponse = mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("电机异响")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number firstOrderId = com.jayway.jsonpath.JsonPath.read(firstResponse, "$.data.id");

        Set<String> keys = stringRedisTemplate.keys("idempotency:order:create:*");
        if (keys != null) {
            stringRedisTemplate.delete(keys);
        }

        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("电机异响")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstOrderId.longValue()));

        long count = repairOrderService.lambdaQuery()
                .eq(RepairOrder::getRequestUserId, 2L)
                .eq(RepairOrder::getIdempotencyKey, idempotencyKey)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadShouldReturn409() throws Exception {
        String idempotencyKey = "conflict-key-" + UUID.randomUUID();
        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("第一次请求")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("同一个键但内容不同")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(21004));
    }

    @Test
    void operationLogShouldKeepCallerTraceId() throws Exception {
        String traceId = "order-it-" + UUID.randomUUID();
        mockMvc.perform(post("/orders")
                        .header("Authorization", token)
                        .header("Idempotency-Key", "audit-" + UUID.randomUUID())
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("审计日志测试")))
                .andExpect(status().isOk());

        OperationLog operationLog = operationLogMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getTraceId, traceId)
        );
        assertThat(operationLog).isNotNull();
        assertThat(operationLog.getOperatorId()).isEqualTo(2L);
        assertThat(operationLog.getAction()).isEqualTo("create");
        assertThat(operationLog.getResult()).isEqualTo(1);
        operationLogMapper.deleteById(operationLog.getId());
    }

    private String validBody(String description) {
        return """
                {"deviceId": %d, "description": "%s", "priority": 2}
                """.formatted(deviceId, description);
    }
}
