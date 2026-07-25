package com.zoee.equipops.auth.service.impl;

import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionCheckServiceImpl implements PermissionCheckService {
    private final PermissionService permissionService;

    /**
     * - PermissionMapper：只给 PermissionServiceImpl 用（它就是这个 Mapper 的唯一入口）
     * - PermissionServiceImpl：负责 "把用户的权限集查出来变成 Set" 这件事
     * - PermissionCheckServiceImpl：只负责 "拿着这个 Set 判断 contains"
     *
     * 如果直接注入 PermissionMapper，那就变成：
     * - 调用链直接穿透到了上一层的内部实现（PermissionServiceImpl 之外出现了第二条查权限的路）
     * - 将来改类型（比如从 new HashSet<>() 改成别的集合）就变成两个地方都要改
     * - Day13 在 PermissionService 上加缓存，结果 PermissionCheckService 因为走的是 Mapper 短路，根本没享受到缓存
     * @param userId
     * @param permCode
     * @return 是否有权限
     */
    @Override
    public boolean hasPerm(Long userId, String permCode) {
        Set<String> permissions = permissionService.listPermissionCodesByUser(userId);
        return permissions.contains(permCode);
    }
}
