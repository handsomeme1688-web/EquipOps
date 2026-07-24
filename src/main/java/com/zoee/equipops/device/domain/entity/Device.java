package com.zoee.equipops.device.domain.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import com.zoee.equipops.device.enums.DeviceStatus;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("device")
public class Device {
    private Long id;
    private String code;
    private Long deptId;
    private Long ownerId;
    private String name;
    private String model;
    private String location;
    private DeviceStatus status;
    private String description;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
}
