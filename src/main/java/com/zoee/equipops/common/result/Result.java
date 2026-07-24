package com.zoee.equipops.common.result;

public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    public static <T> Result<T> success(){
        Result<T> result= new Result<>();
        result.code=1;
        result.msg="success";
        return result;
    }

    public static <T> Result<T> success(T data){
        Result<T> result= new Result<>();
        result.code=1;
        result.msg="success";
        result.data=data;
        return result;

    }

    public static <T> Result<T> error(String msg){
        Result<T> result = new Result<>();
        result.code=0;
        result.msg=msg;
        return result;
    }
}
