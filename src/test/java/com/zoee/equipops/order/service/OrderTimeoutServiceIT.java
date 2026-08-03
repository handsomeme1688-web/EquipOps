package com.zoee.equipops.order.service;

import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.enums.OrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "equipops.order.timeout-scan.enabled=false")
@Import(TestcontainersConfiguration.class)
class OrderTimeoutServiceIT {

    @Autowired
    private OrderTimeoutService timeoutService;
    @Autowired
    private RepairOrderService repairOrderService;
    @Autowired
    private DeviceService deviceService;

    private final List<Long> orderIds = new ArrayList<>();
    private Long deviceId;

    @BeforeEach
    void setUp() {
        Device device = new Device();
        device.setCode("TIMEOUT-IT-" + UUID.randomUUID());
        device.setName("超时任务测试设备");
        device.setModel("T-1");
        device.setLocation("测试区");
        device.setDeptId(2L);
        device.setOwnerId(2L);
        device.setStatus(DeviceStatus.NORMAL);
        deviceService.save(device);
        deviceId = device.getId();
    }

    @AfterEach
    void tearDown() {
        orderIds.forEach(repairOrderService::removeById);
        deviceService.removeById(deviceId);
    }

    @Test
    void repeatedScanIsIdempotentAndExactly24HoursIsNotMarked() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0, 0);
        Long olderOrderId = savePendingOrder(now.minusHours(24).minusSeconds(1));
        Long boundaryOrderId = savePendingOrder(now.minusHours(24));

        assertThat(timeoutService.markTimedOutOrders(now)).isEqualTo(1);
        assertThat(timeoutService.markTimedOutOrders(now)).isZero();

        RepairOrder older = repairOrderService.getById(olderOrderId);
        RepairOrder boundary = repairOrderService.getById(boundaryOrderId);
        assertThat(older.getTimedOut()).isTrue();
        assertThat(older.getTimeoutTime()).isEqualTo(now);
        assertThat(older.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(boundary.getTimedOut()).isFalse();
        assertThat(boundary.getTimeoutTime()).isNull();
    }

    private Long savePendingOrder(LocalDateTime requestTime) {
        RepairOrder order = new RepairOrder();
        order.setDeviceId(deviceId);
        order.setRequestUserId(2L);
        order.setRequestTime(requestTime);
        order.setVersion(0);
        order.setIdempotencyKey(UUID.randomUUID().toString());
        order.setRequestHash(UUID.randomUUID().toString().replace("-", "") + "0".repeat(32));
        order.setDeptId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription("超时边界测试");
        order.setPriority(2);
        order.setCreateBy(2L);
        order.setCreateTime(requestTime);
        order.setTimedOut(false);
        repairOrderService.save(order);
        orderIds.add(order.getId());
        return order.getId();
    }
}
