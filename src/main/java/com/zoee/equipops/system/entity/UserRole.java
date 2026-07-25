package com.zoee.equipops.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRole {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
}
