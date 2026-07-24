package com.zoee.equipops.common.exception;

import com.zoee.equipops.common.result.ResultCode;
import lombok.Getter;

// BizException 只用 @Getter 不需要 @Setter，因为 resultCode 是 final 的，构造时传入后就不可变了。
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
