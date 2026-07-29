package com.zoee.equipops.device.controller;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.device.domain.vo.DeviceFileVO;
import com.zoee.equipops.device.service.DeviceFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceFileController {

    private final DeviceFileService deviceFileService;

    @PostMapping("/{deviceId}/files")
    public Result<DeviceFileVO> upload(@PathVariable Long deviceId, @RequestParam("file") MultipartFile file){
        return Result.success(deviceFileService.upload(deviceId,file));
    }

    /**
     * 这个统一返回的约定有个唯一例外：当响应体不是 JSON 时。
     *
     * download 返回的是文件字节流（Content-Type: image/jpeg），Result 是 JSON 包装（Content-Type: application/json）
     * 把二进制文件塞进 JSON 的 data 字段里技术上可行（Base64 编码），但文件体积膨胀 33%，而且前端拿到了要再解码，没有任何人这样做。
     * @param fileId
     * @param response
     * @return
     * @throws IOException
     */
    @GetMapping("/files/{fileId}/download")
    public void download(@PathVariable Long fileId, HttpServletResponse response) throws IOException {
        deviceFileService.download(fileId,response);
    }

    @DeleteMapping("/files/{fileId}")
    public Result<Void> delete(@PathVariable Long fileId){
        deviceFileService.delete(fileId);
        return Result.success();
    }

    @GetMapping("/{deviceId}/files")
    public Result<List<DeviceFileVO>> listByDeviceId(@PathVariable Long deviceId){
        return Result.success(deviceFileService.listByDeviceId(deviceId));
    }



}
