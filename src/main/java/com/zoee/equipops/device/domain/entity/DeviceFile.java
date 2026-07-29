package com.zoee.equipops.device.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("device_file")
public class DeviceFile {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long deviceId;
    private String fileName;
    private String storageKey;
    private Long size;
    private String contentType;
    private Long uploadBy;
    private LocalDateTime uploadTime;
}
