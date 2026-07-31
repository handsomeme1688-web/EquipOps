package com.zoee.equipops.device.service;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.device.domain.dto.DeviceUpdateDTO;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import com.zoee.equipops.device.enums.DeviceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Day 13 设备缓存集成测试。
 *
 * <p>用 Testcontainers 起真 MySQL + Redis，验证：
 * <ol>
 *   <li>第一次查 → 缓存未命中 → 查库回填 Redis</li>
 *   <li>第二次查 → 缓存命中</li>
 *   <li>更新设备 → 缓存被删 → 下次读到新值</li>
 *   <li>查不存在的设备 → 空标记缓存（穿透防护）</li>
 * </ol>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("设备缓存集成测试（Day 13）")
class DeviceCacheIT {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Long deviceId;
    private String cacheKey;

    @BeforeEach
    void setUp() {
        // 用 admin 身份，isAdmin() 通过，跳过 deptId 校验
        UserContext.setUserId(1L);
        UserContext.setDeptId(1L);

        // 造一个测试设备
        Device device = new Device();
        device.setCode("CACHE-TEST-001");
        device.setName("缓存测试设备");
        device.setModel("TEST-M1");
        device.setLocation("测试位置");
        device.setDeptId(1L);
        device.setOwnerId(1L);
        device.setStatus(DeviceStatus.NORMAL);
        deviceService.save(device);
        deviceId = device.getId();
        cacheKey = "device:detail:" + deviceId;

        // 确保缓存起始是干净的
        redisTemplate.delete(cacheKey);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(cacheKey);
        UserContext.remove();
    }

    // ════════════════ ① 第一次未命中 → 查库回填 ════════════════

    @Test
    @DisplayName("① 第一次查 → 缓存未命中 → 查库回填 Redis")
    void shouldBackfillCacheOnFirstQuery() {
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        DeviceVO vo = deviceService.detail(deviceId);
        assertThat(vo).isNotNull();
        assertThat(vo.getName()).isEqualTo("缓存测试设备");

        // 缓存已回填
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(cacheKey)).isNotNull();
    }

    // ════════════════ ② 第二次命中 ════════════════

    @Test
    @DisplayName("② 第二次查 → 缓存命中 → 返回正确数据")
    void shouldHitCacheOnSecondQuery() {
        DeviceVO vo1 = deviceService.detail(deviceId);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        DeviceVO vo2 = deviceService.detail(deviceId);
        assertThat(vo2.getName()).isEqualTo(vo1.getName());
        assertThat(vo2.getId()).isEqualTo(vo1.getId());
    }

    // ════════════════ ③ 更新后缓存被删 → 下次读到新值 ════════════════

    @Test
    @DisplayName("③ 更新设备 → 缓存被删 → 下次读到新值")
    void shouldEvictCacheAfterUpdate() {
        deviceService.detail(deviceId);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        DeviceUpdateDTO dto = new DeviceUpdateDTO();
        dto.setName("修改后的名称");
        dto.setModel("TEST-M1");
        dto.setLocation("测试位置");
        dto.setOwnerId(1L);
        deviceService.update(deviceId, dto);

        // 缓存被删
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // 再查 → 读到新值
        DeviceVO vo = deviceService.detail(deviceId);
        assertThat(vo.getName()).isEqualTo("修改后的名称");
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    // ════════════════ ④ 空值缓存（穿透防护）═══════════════

    @Test
    @DisplayName("④ 查不存在的设备 → 空标记缓存 → 再次查不穿透")
    void shouldCacheNullMarkerForNonExistentDevice() {
        String ghostKey = "device:detail:99999";
        redisTemplate.delete(ghostKey);

        // 第一次查不存在设备
        assertThatThrownBy(() -> deviceService.detail(99999L))
                .isInstanceOf(BizException.class);

        // 空标记已缓存
        assertThat(redisTemplate.hasKey(ghostKey)).isTrue();
        assertThat(redisTemplate.opsForValue().get(ghostKey)).isEqualTo("NULL");

        // 第二次查 → 同样抛异常（走缓存，不透到 DB）
        assertThatThrownBy(() -> deviceService.detail(99999L))
                .isInstanceOf(BizException.class);

        redisTemplate.delete(ghostKey);
    }
}
