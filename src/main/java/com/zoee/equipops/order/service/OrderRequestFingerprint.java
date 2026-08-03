package com.zoee.equipops.order.service;

import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OrderRequestFingerprint {

    public String calculate(RepairOrderDTO request) {
        String canonical = String.valueOf(request.getDeviceId()) + "\n"
                + normalize(request.getDescription()) + "\n"
                + String.valueOf(request.getPriority());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
