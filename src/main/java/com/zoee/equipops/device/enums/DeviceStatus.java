package com.zoee.equipops.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeviceStatus {
    DISABLED(0, "停用"),
    NORMAL(1, "正常"),
    REPAIRING(2, "维修中"),
    SCRAPPED(3, "报废");

    @EnumValue
    private final int code;
    @JsonValue
    private final String description;

    DeviceStatus(int code,String description){
        this.code=code;
        this.description=description;
    }

    public int getCode(){return code;};
    public String getDescription(){return description;}

    public static DeviceStatus findByCode(int code){
//        if (code==null) return null;
        for (DeviceStatus status:DeviceStatus.values()){
            if (status.getCode()==code){return status;}
        }
        return null;

    }


}
