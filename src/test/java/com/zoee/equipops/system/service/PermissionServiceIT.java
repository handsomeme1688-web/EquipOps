package com.zoee.equipops.system.service;

import com.zoee.equipops.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Day 10 集成测试：多角色权限并集。
 *
 * <p>验证 user_role 表真正的多对多能力——
 * 一个用户绑两个角色时，权限集是两个角色权限的并集，而非只取其一。
 *
 * <p>类级 @Transactional 保证测试结束后角色分配被回滚，
 * 种子数据（zhangsan 仅 EMPLOYEE）不变，不影响其他测试。
 *
 * @author zoe
 * @since 2026-07-25 Day 10
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("多角色权限并集集成测试（Day 10）")
class PermissionServiceIT {

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PermissionService permissionService;

    /**
     * V2 种子数据：
     * <pre>
     *   zhangsan (id=2)  → EMPLOYEE (id=1)
     *   EMPLOYEE 权限 = device:view, order:view, order:create, order:audit, order:cancel (5 项)
     *   DEPT_MANAGER 权限 = 上述 5 项 + device:create/update/delete, system:dept:view, system:user:view (10 项)
     *   并集 = 10 项
     * </pre>
     */
    private static final Long ZHANGSAN_ID = 2L;
    private static final Long EMPLOYEE_ROLE_ID = 1L;
    private static final Long DEPT_MANAGER_ROLE_ID = 2L;

    @Test
    @DisplayName("单角色 EMPLOYEE → 5 项权限")
    void shouldReturnEmployeePermissionsOnly() {
        // zhangsan 当前仅 EMPLOYEE（V2 种子数据）
        Set<String> permissions = permissionService.listPermissionCodesByUser(ZHANGSAN_ID);

        assertThat(permissions)
                .as("单角色 EMPLOYEE 应有 5 项权限")
                .hasSize(5)
                .contains("device:view", "order:view", "order:create", "order:audit", "order:cancel");
        // EMPLOYEE 没有 device:create（那是 DEPT_MANAGER 的权限）
        assertThat(permissions).doesNotContain("device:create");
    }

    @Test
    @DisplayName("绑两个角色 → 权限并集 10 项")
    void shouldReturnUnionWhenUserHasTwoRoles() {
        // 给 zhangsan 同时分配 EMPLOYEE 和 DEPT_MANAGER
        userRoleService.assignRole(ZHANGSAN_ID, List.of(EMPLOYEE_ROLE_ID, DEPT_MANAGER_ROLE_ID));

        Set<String> permissions = permissionService.listPermissionCodesByUser(ZHANGSAN_ID);

        assertThat(permissions)
                .as("两角色并集应有 10 项去重后的权限")
                .hasSize(10);
        // EMPLOYEE 独有（实则 DEPT_MANAGER 也全包含了，但并集里必须有这些）
        assertThat(permissions).contains("device:view", "order:create", "order:audit");
        // DEPT_MANAGER 独有
        assertThat(permissions).contains("device:create", "device:update", "device:delete",
                "system:dept:view", "system:user:view");
    }

    @Test
    @DisplayName("绑两个角色 → 结果去重，device:view 只出现一次")
    void shouldDeduplicateSharedPermissions() {
        userRoleService.assignRole(ZHANGSAN_ID, List.of(EMPLOYEE_ROLE_ID, DEPT_MANAGER_ROLE_ID));

        Set<String> permissions = permissionService.listPermissionCodesByUser(ZHANGSAN_ID);

        // device:view 两个角色都有 → 并集里只应出现一次
        long deviceViewCount = permissions.stream()
                .filter(p -> "device:view".equals(p))
                .count();
        assertThat(deviceViewCount)
                .as("device:view 两个角色都有，但并集应去重为 1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("绑两个角色再撤一个 → 只剩剩余角色的权限")
    void shouldReflectRoleRemoval() {
        // 先绑两个角色
        userRoleService.assignRole(ZHANGSAN_ID, List.of(EMPLOYEE_ROLE_ID, DEPT_MANAGER_ROLE_ID));
        assertThat(permissionService.listPermissionCodesByUser(ZHANGSAN_ID)).hasSize(10);

        // 撤掉 DEPT_MANAGER，只留 EMPLOYEE
        userRoleService.assignRole(ZHANGSAN_ID, List.of(EMPLOYEE_ROLE_ID));

        Set<String> permissions = permissionService.listPermissionCodesByUser(ZHANGSAN_ID);
        assertThat(permissions)
                .as("撤掉 DEPT_MANAGER 后应回退到 EMPLOYEE 的 5 项权限")
                .hasSize(5)
                .doesNotContain("device:create", "system:dept:view");
    }
}
