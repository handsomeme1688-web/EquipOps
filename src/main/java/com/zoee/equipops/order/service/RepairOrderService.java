package com.zoee.equipops.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;

public interface RepairOrderService extends IService<RepairOrder> {
    RepairOrderVO create(RepairOrderDTO repairOrderDTO);
    RepairOrderVO accept(Long orderId);
}
