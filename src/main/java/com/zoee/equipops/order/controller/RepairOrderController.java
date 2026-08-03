package com.zoee.equipops.order.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.dto.StatusUpdateDTO;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;
import com.zoee.equipops.order.service.RepairOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Validated
public class RepairOrderController {
    private final RepairOrderService repairOrderService;

    @OpLog(resourceType = "order", action = "create")
    @PreAuthorize("hasAuthority('order:create')")
    @PostMapping()
    public Result<RepairOrderVO> create(
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key 不能为空")
            @Size(max = 128, message = "Idempotency-Key 长度不能超过 128 个字符")
            String idempotencyKey,
            @RequestBody @Valid RepairOrderDTO repairOrderDTO){
        return Result.success(repairOrderService.create(repairOrderDTO, idempotencyKey));
    }

    @OpLog(resourceType = "order", action = "accept")
    @PreAuthorize("hasAuthority('order:accept')")
    @PostMapping("/{id}/accept")
    public Result<RepairOrderVO> accept(@PathVariable @Positive(message = "工单 ID 必须为正数") Long id){
        return Result.success(repairOrderService.accept(id));
    }

    @OpLog(resourceType = "order", action = "updateStatus")
    @PreAuthorize("hasAnyAuthority('order:accept', 'order:repair', 'order:outsource', 'order:submit', 'order:audit', 'order:cancel')")
    @PutMapping("/{id}/status")
    public Result<RepairOrderVO> updateStatus(
            @PathVariable @Positive(message = "工单 ID 必须为正数") Long id,
            @Valid @RequestBody StatusUpdateDTO statusUpdateDTO){
        return Result.success(repairOrderService.updateStatus(id, statusUpdateDTO.getTargetStatus()));
    }
}
