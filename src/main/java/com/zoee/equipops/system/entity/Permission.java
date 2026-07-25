package com.zoee.equipops.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("permission")
public class Permission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String code;
    private String resource;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
}
