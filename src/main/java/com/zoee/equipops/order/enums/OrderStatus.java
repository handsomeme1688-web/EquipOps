package com.zoee.equipops.order.enums;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    // 1. 定义 7 个枚举常量（对应数据库 0-6）
    PENDING(0, "待受理"),
    ACCEPTED(1, "已接单"),
    IN_REPAIR(2, "维修中"),
    OUTSOURCED(3, "委外中"),
    PENDING_CHECK(4, "待验收"),
    COMPLETED(5, "已完成"),
    CLOSED(6, "已关闭");

    // 2. 定义私有属性：数值和描述
    @EnumValue
    private final int code; // 数据库存的数值
    @JsonValue
    private final String description; // 显示用的中文描述

    // 3. 枚举构造器默认就是 private，不用也不能加 public。
    // 外界无法 new，只有 enum 内部 7 个常量能用它。
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


    // 5. 根据数字代码反查对应的枚举对象
    public static OrderStatus findByCode(int code){
        for (OrderStatus status : OrderStatus.values()) {// OrderStatus.values()返回所有 7 个常量的数组。
            if (status.getCode() == code) {
                return status; // 返回对象，而不是对象属性
            }
        }
        return null;
    }
}
