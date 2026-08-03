package com.zoee.equipops.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.zoee.equipops.order.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("repair_order")
public class RepairOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long deviceId;
    private Long requestUserId;
    private LocalDateTime requestTime;
    @Version
    private Integer version;
    private String idempotencyKey;
    private String requestHash;
    private Long deptId;
    private Long assignId;
    private OrderStatus status;
    private String description;
    private Integer priority;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    private LocalDateTime checkTime;
    private LocalDateTime closeTime;
    private Boolean timedOut;
    private LocalDateTime timeoutTime;
}
