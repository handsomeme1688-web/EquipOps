package com.zoee.equipops.common.exception;

import com.zoee.equipops.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException{
    private final ResultCode resultCode;

    public BizException(ResultCode resultCode){
        super(resultCode.getMessage());
        this.resultCode=resultCode;
    }
    public BizException(ResultCode resultCode,String message){
        super(message);
        this.resultCode=resultCode;
    }

}
