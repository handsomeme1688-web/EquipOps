package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.mapper.UserMapper;
import com.zoee.equipops.system.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
