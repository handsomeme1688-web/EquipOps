package com.zoee.equipops.order.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class RepairOrderTimeoutEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void record(RepairOrderTimedOutEvent event) {
        // 后续可替换为 outbox/MQ；当前只记录已经提交成功的领域事件。
        log.info("repair_order_timed_out orderId={} timedOutAt={}",
                event.orderId(), event.timedOutAt());
    }
}
