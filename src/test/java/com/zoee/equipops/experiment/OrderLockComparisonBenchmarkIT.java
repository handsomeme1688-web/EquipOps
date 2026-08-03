package com.zoee.equipops.experiment;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.enums.OrderStatus;
import com.zoee.equipops.order.mapper.RepairOrderMapper;
import com.zoee.equipops.order.service.RepairOrderService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "equipops.order.timeout-scan.enabled=false")
@Import(TestcontainersConfiguration.class)
@Tag("benchmark")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderLockComparisonBenchmarkIT {

    private static final int THREADS = 50;
    private static final int RUNS = 3;

    @Autowired
    private RepairOrderMapper orderMapper;
    @Autowired
    private RepairOrderService orderService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redisContainer;

    private final List<Long> orderIds = new ArrayList<>();
    private RedissonClient redissonClient;
    private Long deviceId;

    @BeforeAll
    void startRedissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress(
                "redis://" + redisContainer.getHost() + ":"
                        + redisContainer.getMappedPort(6379)
        );
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    void stopRedissonClient() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void createDevice() {
        Device device = new Device();
        device.setCode("LOCK-BENCH-" + UUID.randomUUID());
        device.setName("锁实验设备");
        device.setModel("BENCH-1");
        device.setLocation("实验区");
        device.setDeptId(2L);
        device.setOwnerId(2L);
        device.setStatus(DeviceStatus.NORMAL);
        deviceService.save(device);
        deviceId = device.getId();
    }

    @AfterEach
    void cleanData() {
        orderIds.forEach(orderService::removeById);
        orderIds.clear();
        deviceService.removeById(deviceId);
    }

    @Test
    void compareDbCasAndRedissonWithoutAssertingWhichIsFaster() throws Exception {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int run = 1; run <= RUNS; run++) {
            results.add(runDbCas(run, newPendingOrder()));
            results.add(runRedisson(run, newPendingOrder()));
        }

        assertThat(results).allSatisfy(result -> {
            assertThat(result.successCount()).isEqualTo(1);
            assertThat(result.durationMillis()).isGreaterThanOrEqualTo(0);
        });
        writeRawResults(results);
    }

    private BenchmarkResult runDbCas(int run, Long orderId) throws InterruptedException {
        return runConcurrent(run, "db-cas", index -> orderMapper.update(
                null,
                new LambdaUpdateWrapper<RepairOrder>()
                        .eq(RepairOrder::getId, orderId)
                        .eq(RepairOrder::getStatus, OrderStatus.PENDING)
                        .set(RepairOrder::getStatus, OrderStatus.ACCEPTED)
                        .set(RepairOrder::getAssignId, 10_000L + index)
                        .set(RepairOrder::getAcceptTime, LocalDateTime.now())
        ) == 1);
    }

    private BenchmarkResult runRedisson(int run, Long orderId) throws InterruptedException {
        return runConcurrent(run, "redisson-rlock", index -> {
            RLock lock = redissonClient.getLock("benchmark:order:accept:" + orderId);
            boolean acquired = lock.tryLock(0, 5, TimeUnit.SECONDS);
            if (!acquired) {
                return false;
            }
            try {
                RepairOrder current = orderMapper.selectById(orderId);
                if (current == null || current.getStatus() != OrderStatus.PENDING) {
                    return false;
                }
                return orderMapper.update(
                        null,
                        new LambdaUpdateWrapper<RepairOrder>()
                                .eq(RepairOrder::getId, orderId)
                                .set(RepairOrder::getStatus, OrderStatus.ACCEPTED)
                                .set(RepairOrder::getAssignId, 20_000L + index)
                                .set(RepairOrder::getAcceptTime, LocalDateTime.now())
                ) == 1;
            } finally {
                lock.unlock();
            }
        });
    }

    private BenchmarkResult runConcurrent(
            int run,
            String scheme,
            CheckedOperation operation) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger successCount = new AtomicInteger();

        for (int index = 0; index < THREADS; index++) {
            int engineerIndex = index;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (operation.execute(engineerIndex)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 异常会反映为成功数不等于 1，最终断言会失败。
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        long startNanos = System.nanoTime();
        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        executor.shutdownNow();
        assertThat(completed).isTrue();
        return new BenchmarkResult(run, scheme, THREADS, durationMillis, successCount.get());
    }

    private Long newPendingOrder() {
        RepairOrder order = new RepairOrder();
        order.setDeviceId(deviceId);
        order.setRequestUserId(2L);
        order.setRequestTime(LocalDateTime.now());
        order.setVersion(0);
        order.setIdempotencyKey(UUID.randomUUID().toString());
        order.setRequestHash("a".repeat(64));
        order.setDeptId(2L);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription("并发方案对照实验");
        order.setPriority(2);
        order.setCreateBy(2L);
        order.setCreateTime(LocalDateTime.now());
        order.setTimedOut(false);
        orderService.save(order);
        orderIds.add(order.getId());
        return order.getId();
    }

    private void writeRawResults(List<BenchmarkResult> results) throws IOException {
        Path output = Path.of("target", "benchmark", "order-lock-comparison.csv");
        Files.createDirectories(output.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("run,scheme,threads,duration_ms,success_count");
        results.forEach(result -> lines.add(
                result.run() + "," + result.scheme() + "," + result.threads() + ","
                        + result.durationMillis() + "," + result.successCount()
        ));
        Files.write(
                output,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    @FunctionalInterface
    private interface CheckedOperation {
        boolean execute(int engineerIndex) throws Exception;
    }

    private record BenchmarkResult(
            int run,
            String scheme,
            int threads,
            long durationMillis,
            int successCount
    ) {
    }
}
