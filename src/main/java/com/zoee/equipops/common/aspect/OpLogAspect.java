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

/**
 * 整个流程
 *
 * 请求: POST /orders
 *   ↓
 * Spring Security Filter
 *   ↓
 * DispatcherServlet
 *   ↓
 * OpLogAspect.around()  ← AOP 拦截到 @OpLog
 *   ├─ 写日志：operatorId, resourceType="order", action="create"
 *   ├─ joinPoint.proceed() → RepairOrderController.create(dto)
 *   │     ├─ 成功 → result=1
 *   │     └─ 失败 → result=0, errorMsg=...
 *   └─ finally → operationLogMapper.insert(log) → 入库
 */
@Aspect // 告诉 Spring "这是一个 AOP 切面，拦截方法的逻辑都在这里"
@Component
@RequiredArgsConstructor
public class OpLogAspect { // 切面类

    private final OperationLogMapper operationLogMapper;
    private final UserService userService;
    private final HttpServletRequest request; //用来拿客户端 IP

    /**
     * @Around：环绕通知——在目标方法前后都执行, 还有 @Before（仅之前）、@After（仅之后）
     * "@annotation(opLog)"：拦截条件——带 @OpLog 注解的方法。
     * @param joinPoint: 被拦截的方法的"遥控器"，调 joinPoint.proceed() 就是执行原方法。
     * @param opLog : - OpLog opLog：Spring 自动把方法上的 @OpLog 注解实例注入进来，你可以直接读 opLog.resourceType()。
     * @return
     * @throws Throwable
     */
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
