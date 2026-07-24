package com.zoee.equipops.device.domain.query;

import com.zoee.equipops.device.enums.DeviceStatus;
import lombok.Data;

@Data
public class DeviceQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String name; // 模糊匹配
    private String code; // 精确匹配
    private DeviceStatus status;
    private Long deptId;
    private Long ownerId;
}
