package com.zoee.equipops.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zoee.equipops.system.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
    List<String> selectPermissionCodesByUser(@Param("userId") Long userId);
}
