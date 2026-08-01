package com.zoee.equipops.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zoee.equipops.TestcontainersConfiguration;
import com.zoee.equipops.system.entity.RolePermission;
import com.zoee.equipops.system.mapper.RolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 角色权限事务回滚集成测试。
 *
 * <p>验证 {@link RolePermissionService#assignPermission} 的事务回滚行为：
 * 先删旧权限 → 再批量插新的 —— 两步之间若抛异常，delete 必须回滚，
 * 数据库不允许残留"旧数据已被删但新数据还没写入"的中间态。
 *
 * <p>本类不加 {@code @Transactional}：
 * 加在类上会把被测方法纳入测试线程的事务，这样即使被测方法抛异常，
 * 也是测试线程的事务回滚，而非被测方法自己的事务回滚——
 * 就无法证明"Service 的 @Transactional 真的会回滚"。
 * 去掉类级事务、让 Service 自己管理事务边界，测的才是真实行为。
 *
 * <p>测试数据直接复用 V2 种子数据，不额外构造，测试间无副作用
 *（被测方法的异常导致事务回滚，种子数据原封不动）。
 *
 * @author zoe
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("角色权限事务回滚集成测试")
class RolePermissionServiceIT {

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    /**
     * 被测角色 —— 复用 V2 种子数据的"系统管理员"(id=5)，
     * 它有 7 条 role_permission 记录。
     */
    private static final Long ADMIN_ROLE_ID = 5L;
    private static final int ADMIN_SEED_PERMISSION_COUNT = 7;

    private int originalCount;

    @BeforeEach
    void recordOriginalCount() {
        originalCount = countPermissionsForRole(ADMIN_ROLE_ID);
    }

    @Test
    @DisplayName("分配权限中途抛异常 → delete 回滚，旧 7 条记录完好无损")
    void shouldRollbackDeleteWhenExceptionOccurs() {
        assertThatThrownBy(() ->
                rolePermissionService.assignPermission(ADMIN_ROLE_ID, List.of(1L, 1L))
        )
                .isInstanceOf(RuntimeException.class);

        // 事务回滚后，旧记录必须一条不少
        int countAfterRollback = countPermissionsForRole(ADMIN_ROLE_ID);
        assertThat(countAfterRollback)
                .as("回滚后 role_permission 记录数应与异常前一致")
                .isEqualTo(originalCount);
        assertThat(countAfterRollback)
                .as("种子数据：ADMIN 角色应有 %d 条权限", ADMIN_SEED_PERMISSION_COUNT)
                .isEqualTo(ADMIN_SEED_PERMISSION_COUNT);
    }

    @Test
    @DisplayName("两次调用均抛异常 → 种子数据始终不变（幂等验证）")
    void shouldPreserveSeedDataAfterRepeatedFailures() {
        assertThatThrownBy(() ->
                rolePermissionService.assignPermission(ADMIN_ROLE_ID, List.of(1L, 1L))
        ).isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() ->
                rolePermissionService.assignPermission(ADMIN_ROLE_ID, List.of(2L, 2L))
        ).isInstanceOf(RuntimeException.class);

        // 两次失败后，种子数据纹丝不动
        assertThat(countPermissionsForRole(ADMIN_ROLE_ID))
                .isEqualTo(ADMIN_SEED_PERMISSION_COUNT);
    }

    // ──────────────── helper ────────────────

    private int countPermissionsForRole(Long roleId) {
        return Math.toIntExact(rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
        ));
    }
}
