package com.zoee.equipops.order.service;

import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.order.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import static com.zoee.equipops.order.enums.OrderStatus.ACCEPTED;
import static com.zoee.equipops.order.enums.OrderStatus.CLOSED;
import static com.zoee.equipops.order.enums.OrderStatus.COMPLETED;
import static com.zoee.equipops.order.enums.OrderStatus.IN_REPAIR;
import static com.zoee.equipops.order.enums.OrderStatus.OUTSOURCED;
import static com.zoee.equipops.order.enums.OrderStatus.PENDING;
import static com.zoee.equipops.order.enums.OrderStatus.PENDING_CHECK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateServiceTest {
    private final OrderStateService service = new OrderStateService();

    @ParameterizedTest(name = "{0} -> {1} 应允许")
    @MethodSource("legalTransitions")
    void shouldAllowLegalTransitions(OrderStatus from, OrderStatus to) {
        assertThatCode(() -> service.validateTransition(from, to))
                .doesNotThrowAnyException();
        assertThat(service.canTransit(from, to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} 应拒绝")
    @MethodSource("illegalTransitions")
    void shouldRejectIllegalTransitions(OrderStatus from, OrderStatus to) {
        assertThatThrownBy(() -> service.validateTransition(from, to))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getResultCode())
                        .isEqualTo(ResultCode.ORDER_STATUS_ILLEGAL));
        assertThat(service.canTransit(from, to)).isFalse();
    }

    @Test
    void shouldRejectNullTransitionWithBadRequest() {
        assertThatThrownBy(() -> service.validateTransition(null, PENDING))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getResultCode())
                        .isEqualTo(ResultCode.BAD_REQUEST));
    }

    @Test
    void shouldReturnAllowedTargetsAndTerminalStates() {
        assertThat(service.allowedTargets(PENDING))
                .containsExactlyInAnyOrder(ACCEPTED, CLOSED);
        assertThat(service.allowedTargets(COMPLETED)).isEmpty();
        assertThat(service.allowedTargets(CLOSED)).isEmpty();
        assertThat(service.isTerminal(COMPLETED)).isTrue();
        assertThat(service.isTerminal(CLOSED)).isTrue();
        assertThat(service.allowedTargets(null)).isEqualTo(Collections.emptySet());
    }

    private static Stream<Arguments> legalTransitions() {
        return Stream.of(
                Arguments.of(PENDING, ACCEPTED),
                Arguments.of(PENDING, CLOSED),
                Arguments.of(ACCEPTED, IN_REPAIR),
                Arguments.of(ACCEPTED, CLOSED),
                Arguments.of(IN_REPAIR, PENDING_CHECK),
                Arguments.of(IN_REPAIR, OUTSOURCED),
                Arguments.of(OUTSOURCED, PENDING_CHECK),
                Arguments.of(OUTSOURCED, CLOSED),
                Arguments.of(PENDING_CHECK, COMPLETED),
                Arguments.of(PENDING_CHECK, IN_REPAIR)
        );
    }

    private static Stream<Arguments> illegalTransitions() {
        Set<OrderStatus> terminalStates = Set.of(COMPLETED, CLOSED);
        return Stream.concat(
                Stream.of(
                        Arguments.of(PENDING, COMPLETED),
                        Arguments.of(ACCEPTED, PENDING_CHECK),
                        Arguments.of(IN_REPAIR, COMPLETED),
                        Arguments.of(PENDING_CHECK, CLOSED)
                ),
                terminalStates.stream().map(status -> Arguments.of(status, PENDING))
        );
    }
}
