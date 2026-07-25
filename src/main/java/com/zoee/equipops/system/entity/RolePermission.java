package com.zoee.equipops.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@TableName("role_permission")
public class RolePermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long roleId;
    private Long permissionId;
}
