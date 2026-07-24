package com.zoee.equipops.device.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.zoee.equipops.device.enums.DeviceStatus;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("device")
public class Device {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private Long deptId;
    private Long ownerId;
    private String name;
    private String model;
    private String location;
    private DeviceStatus status;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
