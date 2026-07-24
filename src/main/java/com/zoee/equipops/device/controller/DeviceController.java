package com.zoee.equipops.device.controller;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.device.domain.dto.DeviceCreateDTO;
import com.zoee.equipops.device.domain.dto.DeviceUpdateDTO;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import com.zoee.equipops.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/devices")
public class DeviceController {
    private final DeviceService deviceService;

    @PostMapping
    Result<DeviceVO> createDevice(DeviceCreateDTO deviceCreateDTO){
        return Result.success(deviceService.createDevice(deviceCreateDTO));
    }

    @PutMapping("/{id}")
    Result<DeviceVO> updateDevice(DeviceUpdateDTO deviceUpdateDTO){
        return Result.success(deviceService.updateDevice(deviceUpdateDTO));
    }

    @DeleteMapping("/{id}")
    Result<DeviceVO> deleteDevice(DeviceUpdateDTO deviceUpdateDTO){
        return Result.success(deviceService.deleteDevice(deviceUpdateDTO));
    }

    @GetMapping("/{id}")


}
