package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.system.entity.Permission;
import com.zoee.equipops.system.mapper.PermissionMapper;
import com.zoee.equipops.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    private final RedisTemplate<String,Object> redisTemplate;

    @Override
    public Set<String> listPermissionCodesByUser(Long userId) {
        String key = "user:permissions:"+userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Collection<?> collection) {
            Set<String> cachedPermissions = new HashSet<>();
            boolean valid = true;
            for (Object item : collection) {
                if (!(item instanceof String permissionCode)) {
                    valid = false;
                    break;
                }
                cachedPermissions.add(permissionCode);
            }
            if (valid) {
                return cachedPermissions;
            }
            // 缓存数据类型与当前序列化约定不一致，删除后回源。
            redisTemplate.delete(key);
        }

        HashSet<String> permissions = new HashSet<>(baseMapper.selectPermissionCodesByUser(userId));
        int ttl;
        if(permissions.isEmpty()){
            ttl = 2*60;
        }else {
            // TTL 加小幅随机值，避免大量权限缓存同时过期。
            ttl = 30 * 60 + (int) (Math.random() * 300);
        }
        redisTemplate.opsForValue().set(key,permissions, Duration.ofSeconds(ttl));
        return permissions;
    }
}
