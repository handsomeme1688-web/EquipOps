package com.zoee.equipops.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;
import com.zoee.equipops.order.enums.OrderStatus;

public interface RepairOrderService extends IService<RepairOrder> {
    RepairOrderVO create(RepairOrderDTO repairOrderDTO, String idempotencyKey);
    RepairOrderVO accept(Long orderId);
    RepairOrderVO updateStatus(Long orderId, OrderStatus orderStatus);
}
