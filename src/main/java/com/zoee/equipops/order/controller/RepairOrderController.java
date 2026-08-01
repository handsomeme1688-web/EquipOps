package com.zoee.equipops.order.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.dto.StatusUpdateDTO;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;
import com.zoee.equipops.order.service.RepairOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class RepairOrderController {
    private final RepairOrderService repairOrderService;

    @OpLog(resourceType = "order", action = "create")
    @PreAuthorize("hasAuthority('order:create')")
    @PostMapping()
    public Result<RepairOrderVO> create(@RequestBody @Valid RepairOrderDTO repairOrderDTO){
        return Result.success(repairOrderService.create(repairOrderDTO));
    }

    @OpLog(resourceType = "order", action = "accept")
    @PreAuthorize("hasAuthority('order:accept')")
    @PostMapping("/{id}/accept")
    public Result<RepairOrderVO> accept(@PathVariable Long id){
        return Result.success(repairOrderService.accept(id));
    }

    @OpLog(resourceType = "order", action = "updateStatus")
    @PreAuthorize("hasAnyAuthority('order:accept', 'order:repair', 'order:outsource', 'order:submit', 'order:audit', 'order:cancel')")
    @PutMapping("/{id}/status")
    public Result<RepairOrderVO> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateDTO statusUpdateDTO){
        return Result.success(repairOrderService.updateStatus(id, statusUpdateDTO.getTargetStatus()));
    }
}
