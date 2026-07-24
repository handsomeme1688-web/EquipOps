package com.zoee.equipops.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(){
        Result<T> result= new Result<>();
        result.code=ResultCode.SUCCESS.getCode();
        result.msg="success";
        return result;
    }

    public static <T> Result<T> success(T data){
        Result<T> result= new Result<>();
        result.code=ResultCode.SUCCESS.getCode();
        result.msg="success";
        result.data=data;
        return result;

    }

    public static <T> Result<T> error(int code,String msg){
        Result<T> result = new Result<>();
        result.code=code;
        result.msg=msg;
        return result;
    }
}
