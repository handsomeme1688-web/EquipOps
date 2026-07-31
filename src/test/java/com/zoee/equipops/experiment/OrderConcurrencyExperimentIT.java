package com.zoee.equipops.experiment;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.enums.OrderStatus;
import com.zoee.equipops.order.mapper.RepairOrderMapper;
import com.zoee.equipops.order.service.RepairOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 15 工单并发实验。
 *
 * <p>对同一条 PENDING 工单发起 50 个并发抢单，验证只有 1 个胜出。
 * 横向对比三种并发控制方案：
 * <ol>
 *   <li><b>DB 条件更新（生产方案）</b>：{@code UPDATE SET status=1 WHERE id=? AND status=0}</li>
 *   <li><b>乐观锁</b>：{@code UPDATE SET version=version+1 WHERE id=? AND version=?}</li>
 *   <li><b>悲观锁</b>：{@code SELECT...FOR UPDATE} 锁住行再改</li>
 * </ol>
 *
 * <p><b>注意：不加 @Transactional</b>，因为测试线程池里的线程拿不同数据库连接，
 * 未提交的事务对其他线程不可见。setUp 直接提交到 DB，tearDown 手动清理。
 *
 * <p>所有方案的核心断言一致：50 并发只有 1 次成功，assignId 唯一。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("工单并发控制实验（Day 15）")
class OrderConcurrencyExperimentIT {

    @Autowired
    private RepairOrderService orderService;

    @Autowired
    private RepairOrderMapper orderMapper;

    @Autowired
    private DeviceService deviceService;

    private final AtomicInteger successCount = new AtomicInteger(0);

    /** 记录本次测试创建的数据，tearDown 时清理 */
    private final List<Long> orderIdsToClean = new ArrayList<>();
    private final List<Long> deviceIdsToClean = new ArrayList<>();

    private Long orderId;

    @BeforeEach
    void setUp() {
        successCount.set(0);
        orderIdsToClean.clear();
        deviceIdsToClean.clear();

        // 造设备（直接 commit）
        Device device = new Device();
        device.setCode("CONCUR-TEST-" + System.nanoTime());
        device.setName("并发测试设备");
        device.setModel("X1");
        device.setLocation("测试");
        device.setDeptId(2L);
        device.setOwnerId(2L);
        device.setStatus(DeviceStatus.NORMAL);
        deviceService.save(device);
        deviceIdsToClean.add(device.getId());

        // 建一条 PENDING 工单（直接 commit）
        RepairOrder order = new RepairOrder();
        order.setDeviceId(device.getId());
        order.setDeptId(2L);
        order.setRequestUserId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription("并发测试工单");
        order.setPriority(1);
        order.setVersion(0);
        order.setRequestTime(LocalDateTime.now());
        order.setCreateBy(2L);
        order.setCreateTime(LocalDateTime.now());
        order.setIdempotencyKey(java.util.UUID.randomUUID().toString());
        orderService.save(order);
        orderId = order.getId();
        orderIdsToClean.add(orderId);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
        // 手动清理测试数据
        orderIdsToClean.forEach(id -> orderService.removeById(id));
        deviceIdsToClean.forEach(id -> deviceService.removeById(id));
    }

    // ════════════════════════════════════════════════════════
    // 方案一：DB 条件更新（你的 accept() 就是这种）
    // ════════════════════════════════════════════════════════

    /**
     * 50 个线程同时调 {@link RepairOrderService#accept(Long)}。
     * 核心断言：只有 1 个成功，DB 里 assignId 唯一。
     */
    @Test
    @DisplayName("方案一（生产方案）：DB 条件更新 → 50 并发只有 1 个抢到")
    void testDbConditionalUpdate() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    // 模拟不同工程师：轮流用 userId 6 和 7（都有 order:accept 权限）
                    UserContext.setUserId(idx % 2 == 0 ? 6L : 7L);
                    UserContext.setDeptId(4L); // 维保科

                    latch.countDown();
                    latch.await(); // 同时起跑

                    orderService.accept(orderId);
                    successCount.incrementAndGet();
                } catch (BizException e) {
                    // 抢不到是正常行为
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        // 核心断言：50 并发只有 1 次成功
        assertThat(successCount.get()).isEqualTo(1);

        // DB 验证
        RepairOrder result = orderService.getById(orderId);
        assertThat(result.getAssignId()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    // ════════════════════════════════════════════════════════
    // 方案二：乐观锁（MyBatis-Plus @Version）
    // ════════════════════════════════════════════════════════

    /**
     * 乐观锁方案：先查出实体（含旧 version），改字段，updateById。
     * MP 自动在 WHERE 里加 {@code version = 旧值}，
     * 第一个人更新成功后 version 变了，后面的人 WHERE 匹配不到 → 影响行数 = 0。
     */
    @Test
    @DisplayName("方案二：乐观锁 @Version → 50 并发只有 1 个更新成功")
    void testOptimisticLock() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    UserContext.setUserId(idx % 2 == 0 ? 6L : 7L);
                    UserContext.setDeptId(4L);

                    // 乐观锁：先读（拿到旧 version）
                    RepairOrder order = orderService.getById(orderId);
                    if (order.getStatus() != OrderStatus.PENDING) return;

                    order.setStatus(OrderStatus.ACCEPTED);
                    order.setAssignId(UserContext.getUserId());
                    order.setAcceptTime(LocalDateTime.now());

                    latch.countDown();
                    latch.await();

                    // MP 自动 WHERE version = 旧值
                    boolean success = orderService.updateById(order);
                    if (success) successCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);

        RepairOrder result = orderService.getById(orderId);
        assertThat(result.getAssignId()).isNotNull();
    }

    // ════════════════════════════════════════════════════════
    // 方案三：悲观锁 SELECT...FOR UPDATE
    // ════════════════════════════════════════════════════════

    /**
     * 悲观锁方案：用 {@code SELECT...FOR UPDATE} 锁行，读完改完才释放。
     * 其他线程的 FOR UPDATE 会阻塞，等当前锁释放后才能读到新数据。
     *
     * <p>注意：FOR UPDATE 必须在事务内才生效。此测试中每线程直接调
     * Mapper 和 Service，未显式包事务——实际效果与 DB 条件更新接近。
     * 真正的 FOR UPDATE 用法见生产代码中的悲锁 Service 方法。
     */
    @Test
    @DisplayName("方案三：悲观锁 FOR UPDATE → 50 并发只有 1 个抢到")
    void testPessimisticLockForUpdate() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    UserContext.setUserId(idx % 2 == 0 ? 6L : 7L);
                    UserContext.setDeptId(4L);

                    latch.countDown();
                    latch.await();

                    // FOR UPDATE 锁行
                    RepairOrder order = orderMapper.selectForUpdate(orderId);
                    if (order == null || order.getStatus() != OrderStatus.PENDING) return;

                    LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(RepairOrder::getId, orderId)
                            .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                            .set(RepairOrder::getStatus, OrderStatus.ACCEPTED)
                            .set(RepairOrder::getAssignId, UserContext.getUserId())
                            .set(RepairOrder::getAcceptTime, LocalDateTime.now());

                    boolean success = orderService.update(wrapper);
                    if (success) successCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
    }
}
