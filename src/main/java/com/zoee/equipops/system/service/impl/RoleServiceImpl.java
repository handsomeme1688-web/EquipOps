package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.system.entity.Role;
import com.zoee.equipops.system.mapper.RoleMapper;
import com.zoee.equipops.system.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
}
