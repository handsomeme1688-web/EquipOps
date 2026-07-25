package com.zoee.equipops.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.system.entity.Permission;

import java.util.Set;

public interface PermissionService extends IService<Permission> {
    Set<String> listPermissionCodesByUser(Long userId);
}
