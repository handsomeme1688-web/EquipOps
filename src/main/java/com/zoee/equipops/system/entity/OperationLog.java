package com.zoee.equipops.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private Long operatorId;
    private String operatorName;
    private String resourceType;
    private Long resourceId;
    private String action;
    private Integer result;      // 0-失败, 1-成功
    private String errorMsg;
    private String ip;
    private LocalDateTime createTime;
}
