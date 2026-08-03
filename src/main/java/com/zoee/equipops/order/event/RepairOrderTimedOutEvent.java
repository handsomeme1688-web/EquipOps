package com.zoee.equipops.order.event;

import java.time.LocalDateTime;

public record RepairOrderTimedOutEvent(Long orderId, LocalDateTime timedOutAt) {
}
