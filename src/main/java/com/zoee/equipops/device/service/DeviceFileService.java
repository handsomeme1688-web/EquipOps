package com.zoee.equipops.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.device.domain.entity.DeviceFile;
import com.zoee.equipops.device.domain.vo.DeviceFileVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DeviceFileService extends IService<DeviceFile> {
    DeviceFileVO upload(Long deviceId, MultipartFile file);
    void download(Long fileId, HttpServletResponse response) throws IOException;
    void delete(Long fileId);
    List<DeviceFileVO> listByDeviceId(Long deviceId);
}
