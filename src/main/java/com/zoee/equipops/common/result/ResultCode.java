package com.zoee.equipops.common.result;

/**
 * 业务码编号规则:5 位数字 AABBB
 *   AA  = 模块号(10通用 / 20设备 / 21工单 ...)
 *   BBB = 模块内流水号(001 起,与 HTTP 状态码无关)
 * HTTP 状态码是独立的一列,不参与业务码编号。
 */
public enum ResultCode {

    // 成功
    SUCCESS(0, 200, "成功"),

    // ===== 10xxx 通用/系统 =====
    BAD_REQUEST(10001, 400, "参数错误"),
    UNAUTHORIZED(10002, 401, "未认证"),
    FORBIDDEN(10003, 403, "无权限"),
    NOT_FOUND(10004, 404, "资源不存在"),
    INTERNAL_ERROR(10005, 500, "服务器繁忙"),

    // ===== 20xxx 设备 =====
    DEVICE_NOT_FOUND(20001, 404, "设备不存在"),
    DEVICE_CODE_EXISTS(20002, 409, "设备编号已存在"),

    // ===== 21xxx 工单 =====
    ORDER_NOT_FOUND(21001, 404, "工单不存在"),
    ORDER_STATUS_ILLEGAL(21002, 409, "工单状态流转非法");

    private final int code;        // 业务码,前端精确判断用
    private final int httpStatus;  // HTTP 状态码,给网关/浏览器
    private final String message;

    ResultCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
}

