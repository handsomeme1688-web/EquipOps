package com.zoee.equipops.common.exception;

import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>>  handleBizException(BizException e){
        ResultCode resultCode = e.getResultCode();
        return ResponseEntity
                .status(resultCode.getHttpStatus())
                .body(Result.error(resultCode.getCode(),e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        return validationError(fieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Map<String, String>>> handleBinding(BindException exception) {
        return validationError(fieldErrors(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMethodValidation(
            HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String parameterName = result.getMethodParameter().getParameterName();
            String key = parameterName == null ? "parameter" : parameterName;
            String message = result.getResolvableErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("参数不合法");
            errors.putIfAbsent(key, message);
        });
        return validationError(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ));
        return validationError(errors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMissingHeader(
            MissingRequestHeaderException exception) {
        return validationError(Map.of(
                exception.getHeaderName(),
                exception.getHeaderName() + " 请求头不能为空"
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Map<String, String>>> handleUnreadableBody(
            HttpMessageNotReadableException exception) {
        return validationError(Map.of("requestBody", "请求体格式错误或字段值无法解析"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Map<String, String>>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        return validationError(Map.of(
                exception.getName(),
                "参数值格式错误或不在允许范围内"
        ));
    }

    private Map<String, String> fieldErrors(Iterable<FieldError> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>();
        fieldErrors.forEach(error -> errors.putIfAbsent(
                error.getField(),
                error.getDefaultMessage() == null ? "字段值不合法" : error.getDefaultMessage()
        ));
        return errors;
    }

    private ResponseEntity<Result<Map<String, String>>> validationError(
            Map<String, String> errors) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(
                        ResultCode.BAD_REQUEST.getCode(),
                        "参数校验失败",
                        errors
                ));
    }

}
