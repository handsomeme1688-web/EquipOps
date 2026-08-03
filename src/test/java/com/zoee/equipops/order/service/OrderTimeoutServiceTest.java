package com.zoee.equipops.order.service;

import com.zoee.equipops.order.event.RepairOrderTimedOutEvent;
import com.zoee.equipops.order.mapper.RepairOrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutServiceTest {

    private final RepairOrderMapper mapper = mock(RepairOrderMapper.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OrderTimeoutService service = new OrderTimeoutService(mapper, publisher, 200);

    @Test
    void shouldOnlyPublishEventForRowsActuallyMarked() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0);
        LocalDateTime cutoff = now.minusHours(24);
        when(mapper.selectPendingTimeoutCandidateIds(cutoff, 200))
                .thenReturn(List.of(101L, 102L));
        when(mapper.markPendingOrderTimedOut(101L, cutoff, now)).thenReturn(1);
        when(mapper.markPendingOrderTimedOut(102L, cutoff, now)).thenReturn(0);

        int marked = service.markTimedOutOrders(now);

        assertThat(marked).isEqualTo(1);
        ArgumentCaptor<RepairOrderTimedOutEvent> eventCaptor =
                ArgumentCaptor.forClass(RepairOrderTimedOutEvent.class);
        verify(publisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(101L);
        assertThat(eventCaptor.getValue().timedOutAt()).isEqualTo(now);
    }

    @Test
    void repeatedScanShouldHaveNoAdditionalEffectWhenConditionalUpdateReturnsZero() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0);
        LocalDateTime cutoff = now.minusHours(24);
        when(mapper.selectPendingTimeoutCandidateIds(cutoff, 200))
                .thenReturn(List.of(101L));
        when(mapper.markPendingOrderTimedOut(101L, cutoff, now))
                .thenReturn(1, 0);

        assertThat(service.markTimedOutOrders(now)).isEqualTo(1);
        assertThat(service.markTimedOutOrders(now)).isZero();

        verify(publisher, times(1)).publishEvent(new RepairOrderTimedOutEvent(101L, now));
    }

    @Test
    void exactlyTwentyFourHoursIsTheStrictCutoff() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 3, 12, 0);

        assertThat(service.timeoutCutoff(now))
                .isEqualTo(LocalDateTime.of(2026, 8, 2, 12, 0));
    }
}
