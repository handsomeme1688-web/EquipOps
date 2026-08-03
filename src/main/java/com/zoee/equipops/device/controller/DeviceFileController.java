package com.zoee.equipops.device.controller;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.device.domain.vo.DeviceFileVO;
import com.zoee.equipops.device.service.DeviceFileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Validated
public class DeviceFileController {

    private final DeviceFileService deviceFileService;

    @PreAuthorize("hasAuthority('device:update')")
    @PostMapping("/{deviceId}/files")
    public Result<DeviceFileVO> upload(@PathVariable @Positive(message = "设备 ID 必须为正数") Long deviceId, @RequestParam("file") MultipartFile file){
        return Result.success(deviceFileService.upload(deviceId,file));
    }

    @GetMapping("/files/{fileId}/download")
    @PreAuthorize("hasAuthority('device:view')")
    public void download(@PathVariable @Positive(message = "文件 ID 必须为正数") Long fileId, HttpServletResponse response) throws IOException {
        deviceFileService.download(fileId,response);
    }

    @DeleteMapping("/files/{fileId}")
    @PreAuthorize("hasAuthority('device:update')")
    public Result<Void> delete(@PathVariable @Positive(message = "文件 ID 必须为正数") Long fileId){
        deviceFileService.delete(fileId);
        return Result.success();
    }

    @GetMapping("/{deviceId}/files")
    @PreAuthorize("hasAuthority('device:view')")
    public Result<List<DeviceFileVO>> listByDeviceId(@PathVariable @Positive(message = "设备 ID 必须为正数") Long deviceId){
        return Result.success(deviceFileService.listByDeviceId(deviceId));
    }
}
