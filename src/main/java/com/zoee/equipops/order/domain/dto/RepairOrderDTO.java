package com.zoee.equipops.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepairOrderDTO {
    private Long deviceId;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过 64 个字符")
    private String idempotencyKey;

    @Size(max = 255)
    private String description;
    private Integer priority;
}
