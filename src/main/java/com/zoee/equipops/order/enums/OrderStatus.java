package com.zoee.equipops.order.enums;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    PENDING(0, "待受理"),
    ACCEPTED(1, "已接单"),
    IN_REPAIR(2, "维修中"),
    OUTSOURCED(3, "委外中"),
    PENDING_CHECK(4, "待验收"),
    COMPLETED(5, "已完成"),
    CLOSED(6, "已关闭");

    @EnumValue
    private final int code;
    @JsonValue
    private final String description;

    OrderStatus(Integer code,String description){
        this.code=code;
        this.description=description;
    }

    public int getCode(){
        return code;
    }
    public String getDescription(){
        return description;
    }


    public static OrderStatus findByCode(int code){
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
