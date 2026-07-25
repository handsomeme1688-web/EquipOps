package com.zoee.equipops.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zoee.equipops.system.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.Set;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
