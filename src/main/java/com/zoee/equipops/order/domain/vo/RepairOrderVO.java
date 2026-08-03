package com.zoee.equipops.order.domain.vo;

import com.zoee.equipops.order.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class RepairOrderVO {
    private Long id;
    private Long deviceId;
    private String requesterName; // 报修人
    private LocalDateTime requestTime;
    private String assignName;
    private OrderStatus status;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime acceptTime;
    private LocalDateTime finishTime;
    private LocalDateTime checkTime;
    private LocalDateTime closeTime;
    private Boolean timedOut;
    private LocalDateTime timeoutTime;
}
