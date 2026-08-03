package com.zoee.equipops.order.domain.dto;

import com.zoee.equipops.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StatusUpdateDTO {
    @NotNull(message = "目标状态不能为空")
    private OrderStatus targetStatus;
}
