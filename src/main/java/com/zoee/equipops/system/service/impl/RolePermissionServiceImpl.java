package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.system.entity.RolePermission;
import com.zoee.equipops.common.service.AfterCommitExecutor;
import com.zoee.equipops.system.entity.UserRole;
import com.zoee.equipops.system.mapper.RolePermissionMapper;
import com.zoee.equipops.system.mapper.UserRoleMapper;
import com.zoee.equipops.system.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper,RolePermission> implements RolePermissionService {
    private final RedisTemplate<String,Object> redisTemplate;
    private final UserRoleMapper userRoleMapper;
    private final AfterCommitExecutor afterCommitExecutor;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void assignPermission(Long roleId, List<Long> permissionIds) {
        remove(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,roleId));
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> newList=new ArrayList<>();
            for (Long pid : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(pid);
                newList.add(rolePermission);
            }
            saveBatch(newList);
        }
        List<UserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, roleId));
        List<String> cacheKeys = userRoles.stream()
                .map(userRole -> "user:permissions:" + userRole.getUserId())
                .toList();
        afterCommitExecutor.execute("evict-role-permission-caches-" + roleId, () -> {
            if (!cacheKeys.isEmpty()) {
                redisTemplate.delete(cacheKeys);
            }
        });
    }
}
