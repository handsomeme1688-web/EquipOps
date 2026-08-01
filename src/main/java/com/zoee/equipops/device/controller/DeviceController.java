package com.zoee.equipops.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.device.domain.dto.DeviceCreateDTO;
import com.zoee.equipops.device.domain.dto.DeviceUpdateDTO;
import com.zoee.equipops.device.domain.query.DeviceQuery;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import com.zoee.equipops.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/devices")
public class DeviceController {
    private final DeviceService deviceService;

    @OpLog(resourceType = "device", action = "create")
    @PostMapping
    public Result<DeviceVO> createDevice(@Valid @RequestBody DeviceCreateDTO deviceCreateDTO){ //@Valid:触发你 DTO 上的校验注解(@NotBlank 等)。不加,校验注解形同虚设
        return Result.success(deviceService.create(deviceCreateDTO));
    }

    @OpLog(resourceType = "device", action = "update")
    @PutMapping("/{id}")
    public Result<DeviceVO> updateDevice(@PathVariable Long id,@Valid @RequestBody DeviceUpdateDTO deviceUpdateDTO){
        return Result.success(deviceService.update(id,deviceUpdateDTO));
    }

    @OpLog(resourceType = "device", action = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> deleteDevice(@PathVariable Long id){
        deviceService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<DeviceVO> detailDevice(@PathVariable Long id){
        return Result.success(deviceService.detail(id));
    }

    @GetMapping("/page")
    public Result<Page<DeviceVO>> pageDevice(@ModelAttribute DeviceQuery deviceQuery){
        return Result.success(deviceService.page(deviceQuery));
    }

}
