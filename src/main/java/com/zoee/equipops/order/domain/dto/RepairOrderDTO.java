package com.zoee.equipops.order.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepairOrderDTO {
    private Long deviceId;
    @Size(max = 255)
    private String description;
    private Integer priority;
}
