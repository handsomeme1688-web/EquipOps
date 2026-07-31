package com.zoee.equipops.order.controller;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;
import com.zoee.equipops.order.service.RepairOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class RepairOrderController {
    private final RepairOrderService repairOrderService;

    @PostMapping()
    public Result<RepairOrderVO> create(@RequestBody @Valid RepairOrderDTO repairOrderDTO){
        return Result.success(repairOrderService.create(repairOrderDTO));
    }

    @PostMapping("/{id}/accept")
    public Result<RepairOrderVO> accept(@PathVariable Long orderId){
        return Result.success(repairOrderService.accept(orderId));
    }
}
