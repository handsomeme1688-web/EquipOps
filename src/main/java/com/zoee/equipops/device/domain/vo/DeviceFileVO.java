package com.zoee.equipops.device.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;


@Data
public class DeviceFileVO {
    private Long id;
    private String fileName;
    private Long size;
    private String contentType;
    private String uploadByName;
    private LocalDateTime uploadTime;
}
