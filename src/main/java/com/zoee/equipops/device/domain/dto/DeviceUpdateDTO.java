package com.zoee.equipops.device.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;


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
