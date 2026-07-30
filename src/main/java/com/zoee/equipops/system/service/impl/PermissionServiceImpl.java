package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.system.entity.Permission;
import com.zoee.equipops.system.mapper.PermissionMapper;
import com.zoee.equipops.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    private final RedisTemplate<String,Object> redisTemplate;
    // 继承了ServiceImpl，不需要再注入一个permissionMapper
    @Override
    public Set<String> listPermissionCodesByUser(Long userId) {
        // 查redis
        String key = "user:permissions:"+userId;
        Object cached = redisTemplate.opsForValue().get(key);
        // 查到就直接返回
        if(cached!=null){return (Set<String>) cached;}
        // 未命中就查数据库,并且写入redis
        HashSet<String> permissions = new HashSet<>(baseMapper.selectPermissionCodesByUser(userId));
        int ttl = 0;
        if(permissions.isEmpty()){
            ttl = 2*60;
        }else {
            // 防止雪崩: TTL 加随机值，防止大量 key 同时过期、同时回源查库把数据库打崩
            // 30分钟 + 随机0~300秒
            // 空值短 TTL（穿透防护）
            ttl = 30 * 60 + (int) (Math.random() * 300);
        }
//        redisTemplate.opsForValue().set(key,permissions,ttl, TimeUnit.SECONDS); // 4.1起已弃用
        redisTemplate.opsForValue().set(key,permissions, Duration.ofSeconds(ttl));
        return permissions;
    }
}
