package com.zoee.equipops.order.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepairOrderDTO {
    @NotNull(message = "设备 ID 不能为空")
    @Positive(message = "设备 ID 必须为正数")
    private Long deviceId;

    @NotBlank(message = "报修内容不能为空")
    @Size(max = 255, message = "报修内容长度不能超过 255 个字符")
    private String description;

    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为 1")
    @Max(value = 3, message = "优先级最大为 3")
    private Integer priority;
}
