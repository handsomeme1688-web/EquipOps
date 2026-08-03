package com.zoee.equipops.order.scheduler;

import com.zoee.equipops.order.service.OrderTimeoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "equipops.order.timeout-scan",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderTimeoutScheduler {

    private final OrderTimeoutService orderTimeoutService;

    @Scheduled(
            cron = "${equipops.order.timeout-scan.cron:0 0/10 * * * *}",
            zone = "${equipops.order.timeout-scan.zone:Asia/Shanghai}"
    )
    public void scan() {
        orderTimeoutService.markTimedOutOrders(LocalDateTime.now());
    }
}
