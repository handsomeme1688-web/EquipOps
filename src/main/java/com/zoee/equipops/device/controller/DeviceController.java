package com.zoee.equipops.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @PostMapping
    public Result<DeviceVO> createDevice(@Valid @RequestBody DeviceCreateDTO deviceCreateDTO){ //@Valid:触发你 DTO 上的校验注解(@NotBlank 等)。不加,校验注解形同虚设
        return Result.success(deviceService.create(deviceCreateDTO));
    }

    @PutMapping("/{id}")
    public Result<DeviceVO> updateDevice(@PathVariable Long id,@Valid @RequestBody DeviceUpdateDTO deviceUpdateDTO){
        return Result.success(deviceService.update(id,deviceUpdateDTO));
    }

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

    /**
     * 请求进来
     *   │
     *   ├─ 一切正常 → Controller: return Result.success()
     *   │              → Spring 默认包 200 OK → 前端收到 200 + body{code:0}
     *   │
     *   └─ 出错 throw BizException
     *                  → Controller 中断，不 return
     *                  → GlobalExceptionHandler 接住
     *                  → return ResponseEntity.status(404).body(Result.error(20001,...))
     *                  → 前端收到 404 + body{code:20001,msg:"设备不存在"}
     */

}
