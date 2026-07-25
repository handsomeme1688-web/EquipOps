package com.zoee.equipops.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.system.entity.RolePermission;

import java.util.List;

public interface RolePermissionService extends IService<RolePermission> {
    void assignPermission(Long roleId, List<Long> permissionIds);
}
