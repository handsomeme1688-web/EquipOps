package com.zoee.equipops.system.service;

import com.zoee.equipops.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限缓存集成测试。
 *
 * <p>用 Testcontainers 起真 MySQL + Redis，验证：
 * <ol>
 *   <li>第一次查权限 → 缓存未命中 → 查库回填 Redis</li>
 *   <li>第二次查 → 缓存命中</li>
 *   <li>改角色权限 → 权限缓存失效 → 读到新权限</li>
 * </ol>
 *
 * <p>测试对象是 {@link PermissionService#listPermissionCodesByUser(Long)}，
 * 它被 {@code PermissionCheckService#hasPerm} 间接调用，是所有接口的权限校验热路径。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("权限缓存集成测试")
class PermissionCacheIT {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String cacheKey;

    /** 被测试的用户 —— zhangsan (id=2)，默认角色 EMPLOYEE，有 5 项权限 */
    private static final Long TEST_USER_ID = 2L;

    @BeforeEach
    void setUp() {
        cacheKey = "user:permissions:" + TEST_USER_ID;
        userRoleService.assignRole(TEST_USER_ID, List.of(1L));
        redisTemplate.delete(cacheKey);
    }

    @AfterEach
    void tearDown() {
        userRoleService.assignRole(TEST_USER_ID, List.of(1L));
        redisTemplate.delete(cacheKey);
    }

    // ════════════════ ① 第一次未命中 → 查库回填 ════════════════

    @Test
    @DisplayName("① 第一次查权限 → 缓存未命中 → 查库回填 Redis")
    void shouldBackfillPermissionCacheOnFirstQuery() {
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        Set<String> permissions = permissionService.listPermissionCodesByUser(TEST_USER_ID);
        assertThat(permissions).isNotEmpty();

        // 缓存已回填
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    // ════════════════ ② 第二次命中 ════════════════

    @Test
    @DisplayName("② 第二次查 → 缓存命中 → 返回与第一次相同的数据")
    void shouldHitPermissionCacheOnSecondQuery() {
        Set<String> first = permissionService.listPermissionCodesByUser(TEST_USER_ID);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        Set<String> second = permissionService.listPermissionCodesByUser(TEST_USER_ID);
        assertThat(second).containsExactlyInAnyOrderElementsOf(first);
    }

    // ════════════════ ⑤ 改角色权限 → 缓存失效 ════════════════

    @Test
    @DisplayName("⑤ 用户角色变更 → 权限缓存被删 → 下次读到新权限")
    void shouldEvictPermissionCacheAfterRoleChange() {
        // 先查一次，回填缓存
        Set<String> beforePermissions = permissionService.listPermissionCodesByUser(TEST_USER_ID);
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // 把 zhangsan 的角色从 EMPLOYEE(1) 改成 ENGINEER(3)
        // ENGINEER 有 6 项权限，和 EMPLOYEE 的 5 项不一样
        userRoleService.assignRole(TEST_USER_ID, List.of(3L));

        // 缓存应被删
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        // 再查 → 应读到 ENGINEER 的权限（含 order:accept, order:repair, order:submit）
        Set<String> afterPermissions = permissionService.listPermissionCodesByUser(TEST_USER_ID);
        assertThat(afterPermissions).isNotEmpty();
        // 新旧权限集不同（角色变了）
        assertThat(afterPermissions).isNotEqualTo(beforePermissions);
        // ENGINEER 特有权限应出现
        assertThat(afterPermissions).contains("order:accept", "order:repair", "order:submit");

        // 缓存已回填新值
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

    }
}
