package com.zoee.equipops.order.service;

import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.order.enums.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.zoee.equipops.order.enums.OrderStatus.ACCEPTED;
import static com.zoee.equipops.order.enums.OrderStatus.CLOSED;
import static com.zoee.equipops.order.enums.OrderStatus.COMPLETED;
import static com.zoee.equipops.order.enums.OrderStatus.IN_REPAIR;
import static com.zoee.equipops.order.enums.OrderStatus.OUTSOURCED;
import static com.zoee.equipops.order.enums.OrderStatus.PENDING;
import static com.zoee.equipops.order.enums.OrderStatus.PENDING_CHECK;

@Service
public class OrderStateService {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = buildTransitions();

    private static Map<OrderStatus, Set<OrderStatus>> buildTransitions() {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(PENDING, Set.of(ACCEPTED, CLOSED));
        transitions.put(ACCEPTED, Set.of(IN_REPAIR, CLOSED));
        transitions.put(IN_REPAIR, Set.of(PENDING_CHECK, OUTSOURCED));
        transitions.put(OUTSOURCED, Set.of(PENDING_CHECK, CLOSED));
        transitions.put(PENDING_CHECK, Set.of(COMPLETED, IN_REPAIR));
        transitions.put(COMPLETED, Collections.emptySet());
        transitions.put(CLOSED, Collections.emptySet());
        return Collections.unmodifiableMap(transitions);
    }

    public boolean canTransit(OrderStatus from, OrderStatus to) {
        return from != null && to != null && TRANSITIONS.get(from).contains(to);
    }

    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "状态流转的起点与终点均不能为空");
        }
        if (!canTransit(from, to)) {
            throw new BizException(
                    ResultCode.ORDER_STATUS_ILLEGAL,
                    "非法的工单状态流转: " + from.getDescription() + " -> " + to.getDescription()
            );
        }
    }

    public Set<OrderStatus> allowedTargets(OrderStatus from) {
        return from == null ? Collections.emptySet() : TRANSITIONS.get(from);
    }

    public boolean isTerminal(OrderStatus status) {
        return status != null && allowedTargets(status).isEmpty();
    }
}
