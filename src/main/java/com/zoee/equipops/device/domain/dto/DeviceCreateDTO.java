package com.zoee.equipops.device.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceCreateDTO {
    @NotNull(message = "设备责任人不能为空")
    @Positive(message = "设备责任人 ID 必须为正数")
    private Long ownerId;

    @NotBlank(message = "设备编号不能为空")
    @Size(max = 50, message = "设备编号长度不能超过 50 个字符")
    private String code;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50, message = "设备名称长度不能超过 50 个字符")
    private String name;

    @NotBlank(message = "设备型号不能为空")
    @Size(max = 50, message = "设备型号长度不能超过 50 个字符")
    private String model;

    @NotBlank(message = "设备位置不能为空")
    @Size(max = 50, message = "设备位置长度不能超过 50 个字符")
    private String location;

    @Size(max = 255, message = "设备描述长度不能超过 255 个字符")
    private String description;
}
