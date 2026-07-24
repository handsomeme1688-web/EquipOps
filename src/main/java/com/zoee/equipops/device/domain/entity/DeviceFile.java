package com.zoee.equipops.device.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("device_file")
public class DeviceFile {
    private Long id;
    private Long deviceId;
    private String fileName;
    private String storageKey;
    private Long size;
    private String contentType;
    private Long updateBy;
    private LocalDateTime updateTime;
}
