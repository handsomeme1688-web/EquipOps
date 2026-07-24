package com.zoee.equipops.device.domain.vo;

import com.zoee.equipops.device.enums.DeviceStatus;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class DeviceVO {
    private Long id;                  // 前端操作凭据（编辑/删除/跳转）
    private String code;              // 设备编号
    private String name;              // 设备名称
    private String model;             // 型号
    private String location;          // 位置
    private DeviceStatus status;      // 状态（@JsonValue 自动转中文）
    private String description;       // 描述

    private Long deptId;              // 点击跳转、编辑回填下拉框
    private String deptName;          // 部门名（列表直接显示）
    private Long ownerId;             // 编辑时回填责任人下拉框
    private String ownerName;         // 责任人姓名

    private LocalDateTime createTime; // 创建时间（列表常有这列）
    private LocalDateTime updateTime; // 最后修改时间

}
