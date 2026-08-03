package com.zoee.equipops.order.service;

import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRequestFingerprintTest {

    private final OrderRequestFingerprint fingerprint = new OrderRequestFingerprint();

    @Test
    void equivalentPayloadShouldProduceSameFingerprint() {
        RepairOrderDTO first = request(10L, "  电机异响  ", 2);
        RepairOrderDTO second = request(10L, "电机异响", 2);

        assertThat(fingerprint.calculate(first))
                .hasSize(64)
                .isEqualTo(fingerprint.calculate(second));
    }

    @Test
    void changedBusinessFieldShouldProduceDifferentFingerprint() {
        RepairOrderDTO first = request(10L, "电机异响", 2);
        RepairOrderDTO second = request(10L, "电机完全停转", 2);

        assertThat(fingerprint.calculate(first))
                .isNotEqualTo(fingerprint.calculate(second));
    }

    private RepairOrderDTO request(Long deviceId, String description, Integer priority) {
        RepairOrderDTO request = new RepairOrderDTO();
        request.setDeviceId(deviceId);
        request.setDescription(description);
        request.setPriority(priority);
        return request;
    }
}
