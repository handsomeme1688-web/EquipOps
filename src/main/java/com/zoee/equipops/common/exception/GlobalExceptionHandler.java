package com.zoee.equipops.common.exception;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.common.result.ResultCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ① 拦截业务异常
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>>  handleBizException(BizException e){
        ResultCode resultCode = e.getResultCode();
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(Result.error(resultCode.getCode(),e.getMessage()));
    }

    // ② 拦截参数校验失败

    // ③ 兜底：意料之外的异常
}
