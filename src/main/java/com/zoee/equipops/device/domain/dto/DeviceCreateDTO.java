package com.zoee.equipops.device.domain.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceCreateDTO {
    @NotBlank(message = "设备责任人不能为空")
    private Long ownerId;

    // @NotNull 只挡 null，@NotBlank 顺便连空格一起挡了。
    @NotBlank(message = "设备编号不能为空")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "设备名称不能为空")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "设备型号不能为空")
    @Size(max = 50)
    private String model;

    @NotBlank(message = "设备位置不能为空")
    @Size(max = 50)
    private String location;

    @Size(max = 255)
    private String description;
}
