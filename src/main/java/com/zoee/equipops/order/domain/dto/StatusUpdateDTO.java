package com.zoee.equipops.order.domain.dto;

import com.zoee.equipops.order.enums.OrderStatus;
import lombok.Data;

@Data
public class StatusUpdateDTO {
    private OrderStatus targetStatus;
}
