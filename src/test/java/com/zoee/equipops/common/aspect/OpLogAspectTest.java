package com.zoee.equipops.common.aspect;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.service.OperationLogWriter;
import com.zoee.equipops.system.entity.OperationLog;
import com.zoee.equipops.system.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpLogAspectTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loggingFailureMustNotReplaceSuccessfulBusinessResult() throws Throwable {
        OperationLogWriter writer = mock(OperationLogWriter.class);
        UserService userService = mock(UserService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        OpLog opLog = mock(OpLog.class);
        OpLogAspect aspect = new OpLogAspect(writer, userService, request);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(2L, null, List.of())
        );
        when(userService.getById(2L)).thenThrow(new RuntimeException("模拟姓名查询失败"));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(opLog.resourceType()).thenReturn("device");
        when(opLog.action()).thenReturn("update");
        when(joinPoint.proceed()).thenReturn("business-result");
        doThrow(new RuntimeException("模拟审计库写入失败"))
                .when(writer).write(any(OperationLog.class));

        Object result = aspect.around(joinPoint, opLog);

        assertThat(result).isEqualTo("business-result");
        verify(joinPoint).proceed();
        ArgumentCaptor<OperationLog> logCaptor = ArgumentCaptor.forClass(OperationLog.class);
        verify(writer).write(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperatorId()).isEqualTo(2L);
        assertThat(logCaptor.getValue().getOperatorName()).isEqualTo("未知");
        assertThat(logCaptor.getValue().getResult()).isEqualTo(1);
    }
}
