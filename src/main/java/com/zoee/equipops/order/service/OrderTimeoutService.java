package com.zoee.equipops.order.service;

import com.zoee.equipops.order.event.RepairOrderTimedOutEvent;
import com.zoee.equipops.order.mapper.RepairOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class OrderTimeoutService {

    private static final long TIMEOUT_HOURS = 24;

    private final RepairOrderMapper repairOrderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final int batchSize;

    public OrderTimeoutService(
            RepairOrderMapper repairOrderMapper,
            ApplicationEventPublisher eventPublisher,
            @Value("${equipops.order.timeout-scan.batch-size:200}") int batchSize) {
        this.repairOrderMapper = repairOrderMapper;
        this.eventPublisher = eventPublisher;
        this.batchSize = batchSize;
    }

    @Transactional(rollbackFor = Exception.class)
    public int markTimedOutOrders(LocalDateTime now) {
        LocalDateTime cutoff = timeoutCutoff(now);
        List<Long> candidateIds = repairOrderMapper
                .selectPendingTimeoutCandidateIds(cutoff, batchSize);

        int markedCount = 0;
        for (Long orderId : candidateIds) {
            int affectedRows = repairOrderMapper.markPendingOrderTimedOut(orderId, cutoff, now);
            if (affectedRows == 1) {
                markedCount++;
                eventPublisher.publishEvent(new RepairOrderTimedOutEvent(orderId, now));
            }
        }

        log.info("order_timeout_scan cutoff={} candidates={} marked={}",
                cutoff, candidateIds.size(), markedCount);
        return markedCount;
    }

    public LocalDateTime timeoutCutoff(LocalDateTime now) {
        return now.minusHours(TIMEOUT_HOURS);
    }
}
