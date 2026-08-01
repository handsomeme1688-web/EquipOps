package com.zoee.equipops.common.service;

import com.zoee.equipops.system.entity.OperationLog;
import com.zoee.equipops.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationLogWriter {
    private final OperationLogMapper operationLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}
