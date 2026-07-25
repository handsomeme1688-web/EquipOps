package com.zoee.equipops.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.system.entity.UserRole;

import java.util.List;

public interface UserRoleService extends IService<UserRole> {
    void assignRole(Long userId, List<Long> roleIds);
}
