package com.zoee.equipops.common.aspect;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.service.OperationLogWriter;
import com.zoee.equipops.system.entity.OperationLog;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OpLogAspect {

    private final OperationLogWriter operationLogWriter;
    private final UserService userService;
    private final HttpServletRequest request;

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        OperationLog log = new OperationLog();
        log.setOperatorId(UserContext.getUserId());
        log.setOperatorName(getOperatorName());
        log.setResourceType(opLog.resourceType());
        log.setAction(opLog.action());
        log.setTraceId(resolveTraceId());
        log.setIp(getClientIp());
        log.setCreateTime(LocalDateTime.now());

        try {
            Object result = joinPoint.proceed();
            log.setResult(1);
            return result;
        } catch (Throwable e) {
            log.setResult(0);
            log.setErrorMsg(truncate(e.getMessage(), 500));
            throw e;
        } finally {
            writeSafely(log);
        }
    }

    private void writeSafely(OperationLog operationLog) {
        // 登录前没有 operator_id，认证接口不记录操作日志；其他误标注的公开接口也不能影响业务结果。
        if (operationLog.getOperatorId() == null) {
            log.warn("Skip operation log without operatorId, traceId={}", operationLog.getTraceId());
            return;
        }
        try {
            operationLogWriter.write(operationLog);
        } catch (RuntimeException e) {
            log.error("Failed to write operation log, traceId={}", operationLog.getTraceId(), e);
        }
    }

    private String getOperatorName() {
        Long userId = UserContext.getUserId();
        if (userId == null) return "未知";
        User user = userService.getById(userId);
        return user != null ? user.getRealName() : "未知";
    }

    private String getClientIp() {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private String resolveTraceId() {
        String traceId = request.getHeader("X-Trace-Id");
        return StringUtils.hasText(traceId)
                ? truncate(traceId, 64)
                : UUID.randomUUID().toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
