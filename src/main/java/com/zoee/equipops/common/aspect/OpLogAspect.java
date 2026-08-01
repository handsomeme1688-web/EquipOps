package com.zoee.equipops.common.aspect;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.system.entity.OperationLog;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.mapper.OperationLogMapper;
import com.zoee.equipops.system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect //  AOP 切面
@Component
@RequiredArgsConstructor
public class OpLogAspect { // 切面类

    private final OperationLogMapper operationLogMapper;
    private final UserService userService;
    private final HttpServletRequest request; //用来拿客户端 IP

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        OperationLog log = new OperationLog();
        log.setOperatorId(UserContext.getUserId());
        log.setOperatorName(getOperatorName());
        log.setResourceType(opLog.resourceType());
        log.setAction(opLog.action());
        log.setIp(getClientIp());
        log.setCreateTime(LocalDateTime.now());

        try {
            Object result = joinPoint.proceed(); // 执行原方法
            log.setResult(1); // 成功
            return result;
        } catch (Throwable e) {
            log.setResult(0); // 失败
            log.setErrorMsg(e.getMessage());
            throw e; // 继续抛，让全局异常处理器处理
        } finally {
            operationLogMapper.insert(log);
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
}
