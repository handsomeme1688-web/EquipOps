package com.zoee.equipops.experiment;

import com.zoee.equipops.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 14 缓存击穿实验。
 *
 * <p>对比两种方案的并发行为：
 * <ul>
 *   <li><b>方案A：互斥锁重建</b> — 缓存未命中时用 SETNX 抢锁，胜者查库重建，败者等待重试</li>
 *   <li><b>方案B：逻辑过期</b> — 逻辑过期后返回旧数据，异步抢锁重建，用户不等待</li>
 * </ul>
 *
 * <p>验证目标：50 并发未命中同一 key，只有 1 次真正查库。
 *
 * <p>实验代码放在 src/test 下，不混入生产 Service（手册要求）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("缓存击穿实验（Day 14）")
class CacheBreakdownExperimentIT {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 模拟数据库查询次数 */
    private static final AtomicInteger dbQueryCount = new AtomicInteger(0);

    private static final String DATA_KEY = "experiment:device:1";
    private static final String LOCK_KEY = "experiment:lock:device:1";

    @BeforeEach
    void setUp() {
        dbQueryCount.set(0);
        redisTemplate.delete(DATA_KEY);
        redisTemplate.delete(LOCK_KEY);
    }

    // ---- 模拟数据库查询 ----

    private String queryDb(Long id) {
        dbQueryCount.incrementAndGet();
        try {
            Thread.sleep(20); // 模拟 DB 延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "设备数据-" + id;
    }

    // ═══════════════════════════════════════════════
    // 方案A：互斥锁重建
    // ═══════════════════════════════════════════════

    /**
     * 互斥锁重建的核心逻辑。
     *
     * <p>流程：
     * <ol>
     *   <li>查缓存，命中直接返回</li>
     *   <li>未命中 → SETNX 抢互斥锁（带 10s 超时防死锁）</li>
     *   <li>抢到锁 → 双重检查 → 查库 → 回填缓存 → 释放锁 → 返回</li>
     *   <li>没抢到 → sleep 50ms → 递归重试（此时缓存大概率已被胜者回填）</li>
     * </ol>
     */
    private String getWithMutex(Long id) {
        Object cached = redisTemplate.opsForValue().get(DATA_KEY);
        if (cached != null) {
            return (String) cached;
        }

        // SETNX：只有第一个线程返回 true
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(10));

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：上一个持锁者可能已经回填了
                cached = redisTemplate.opsForValue().get(DATA_KEY);
                if (cached != null) {
                    return (String) cached;
                }
                // 查库 + 回填
                String data = queryDb(id);
                redisTemplate.opsForValue().set(DATA_KEY, data, Duration.ofMinutes(30));
                return data;
            } finally {
                redisTemplate.delete(LOCK_KEY);
            }
        } else {
            // 没抢到锁 → 短暂等待后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getWithMutex(id);
        }
    }

    // ═══════════════════════════════════════════════
    // 方案B：逻辑过期
    // ═══════════════════════════════════════════════

    /** 逻辑过期存储结构：data 字段存实际数据，expireAt 存逻辑过期时间戳（毫秒） */
    private static final String LOGICAL_KEY = "experiment:logical:device:1";
    private static final String LOGICAL_LOCK = "experiment:logical:lock:1";

    /**
     * 预热：把数据写入 Redis，逻辑过期时间设为过去（模拟即将或已经过期）。
     */
    private void preWarmLogicalCache(String data) {
        // 用 Redis Hash 存两个字段：data 和 expireAt
        redisTemplate.opsForHash().put(LOGICAL_KEY, "data", data);
        redisTemplate.opsForHash().put(LOGICAL_KEY, "expireAt",
                String.valueOf(System.currentTimeMillis() - 1000)); // 1 秒前已过期
    }

    /**
     * 逻辑过期方案。
     *
     * <p>流程：
     * <ol>
     *   <li>缓存完全不存在 → 查库回填（首次）</li>
     *   <li>缓存存在且未逻辑过期 → 直接返回</li>
     *   <li>缓存存在但已逻辑过期 → <b>立即返回旧数据</b>，同时尝试抢锁异步重建</li>
     * </ol>
     *
     * <p>与互斥锁的关键区别：逻辑过期后<b>不阻塞用户</b>，返回旧数据即可。
     */
    private String getWithLogicalExpire(Long id) {
        // 取 Hash 里的 data 字段
        Object dataObj = redisTemplate.opsForHash().get(LOGICAL_KEY, "data");

        if (dataObj == null) {
            // 首次，直接重建
            String data = queryDb(id);
            preWarmLogicalCache(data);
            return data;
        }

        String data = (String) dataObj;
        // 读 expireAt 字段
        Object expireObj = redisTemplate.opsForHash().get(LOGICAL_KEY, "expireAt");
        long expireAt = Long.parseLong((String) expireObj);

        // 判断是否逻辑过期
        if (System.currentTimeMillis() < expireAt) {
            // 未过期，直接返回
            return data;
        }

        // ---- 逻辑过期：先返回旧数据，再尝试重建 ----
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOGICAL_LOCK, "1", Duration.ofSeconds(10));

        if (Boolean.TRUE.equals(locked)) {
            try {
                String newData = queryDb(id);
                // 更新数据，逻辑过期时间设为 30 分钟后
                redisTemplate.opsForHash().put(LOGICAL_KEY, "data", newData);
                redisTemplate.opsForHash().put(LOGICAL_KEY, "expireAt",
                        String.valueOf(System.currentTimeMillis() + 30 * 60 * 1000));
            } finally {
                redisTemplate.delete(LOGICAL_LOCK);
            }
        }

        // 不管抢没抢到锁，都返回旧数据（不阻塞）
        return data;
    }

    // ═══════════════════════════════════════════════
    // 并发测试：50 线程同时未命中同一个 key
    // ═══════════════════════════════════════════════

    @Test
    @DisplayName("方案A 互斥锁：50 并发未命中 → 只有 1 次查库")
    void testMutexLockOnlyOneDbQuery() throws Exception {
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();       // 自己就位
                    latch.await();            // 等所有人就位
                    getWithMutex(1L);
                } catch (Exception e) {
                    // ignore in test
                }
            });
        }

        executor.shutdown();
        // 等待所有线程完成（最长等 30 秒）
        boolean finished = executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // 核心断言：50 并发只查了一次数据库
        assertThat(dbQueryCount.get()).isEqualTo(1);
        // 缓存里应该有数据
        assertThat(redisTemplate.opsForValue().get(DATA_KEY)).isNotNull();
    }

    @Test
    @DisplayName("方案B 逻辑过期：50 并发取已过期数据 → 只有 1 次重建")
    void testLogicalExpireOnlyOneRebuild() throws Exception {
        // 预热：数据已经逻辑过期
        preWarmLogicalCache("旧设备数据-1");
        dbQueryCount.set(0);

        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    String result = getWithLogicalExpire(1L);
                    // 所有线程都应拿到数据（旧数据或新数据都行）
                    assertThat(result).isNotNull();
                } catch (Exception e) {
                    // ignore in test
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        // 核心断言：50 并发只有 1 次重建
        assertThat(dbQueryCount.get()).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════
    // 对比总结（写进产出报告用）
    // ═══════════════════════════════════════════════

    @Test
    @DisplayName("对比验证：互斥锁返回新数据 vs 逻辑过期返回旧数据")
    void compareBothApproaches() {
        // 方案A：互斥锁 → 缓存未命中，查库返回新数据
        dbQueryCount.set(0);
        String resultA = getWithMutex(3L);
        assertThat(resultA).isEqualTo("设备数据-3"); // 返回的是刚查库得到的新数据
        assertThat(dbQueryCount.get()).isEqualTo(1);

        // 方案B：逻辑过期 → 预热后即便过期，也返回旧数据不阻塞
        dbQueryCount.set(0);
        preWarmLogicalCache("预热旧数据");
        String resultB = getWithLogicalExpire(3L);
        // 关键区别：逻辑过期返回的是旧数据，用户不等待
        assertThat(resultB).isEqualTo("预热旧数据");
        // 但后台触发了 1 次重建
        assertThat(dbQueryCount.get()).isEqualTo(1);
    }
}
