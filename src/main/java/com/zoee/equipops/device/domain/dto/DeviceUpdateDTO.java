package com.zoee.equipops.device.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备状态是被业务流程驱动的。
 * 设备"正常→维修中"是因为有人报修了工单，"维修中→正常"是因为工单验收通过了。
 * 也就是说 status 应该由工单流程自动改，不该让用户在"编辑设备"表单里手动选。
 * 如果这样想，status 不进 UpdateDTO。
 */
@Data
public class DeviceUpdateDTO {
    @NotNull(message = "设备责任人不能为空")
    private Long ownerId;

    @NotBlank(message = "设备位置不能为空")
    @Size(max = 50)
    private String location;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "设备型号不能为空")
    @Size(max = 50)
    private String model;

    @Size(max = 255)
    private String description;
}
