package com.zoee.equipops.device.domain.query;

import com.zoee.equipops.device.enums.DeviceStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceQuery {
    @Min(value = 1, message = "页码必须大于等于 1")
    private Integer pageNum = 1;
    @Min(value = 1, message = "每页条数必须大于等于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;
    @Size(max = 50, message = "设备名称查询条件不能超过 50 个字符")
    private String name; // 模糊匹配
    @Size(max = 50, message = "设备编号查询条件不能超过 50 个字符")
    private String code; // 精确匹配
    private DeviceStatus status;
    @Positive(message = "部门 ID 必须为正数")
    private Long deptId;
    @Positive(message = "责任人 ID 必须为正数")
    private Long ownerId;
}
