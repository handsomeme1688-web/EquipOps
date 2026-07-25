package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.system.entity.Permission;
import com.zoee.equipops.system.mapper.PermissionMapper;
import com.zoee.equipops.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    // 继承了ServiceImpl，不需要再注入一个permissionMapper
    @Override
    public Set<String> listPermissionCodesByUser(Long userId) {
        return new HashSet<>(baseMapper.selectPermissionCodesByUser(userId));
    }
}
