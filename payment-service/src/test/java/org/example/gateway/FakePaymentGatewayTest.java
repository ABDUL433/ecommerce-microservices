package org.example.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakePaymentGatewayTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway(new BigDecimal("1000"));

    @Test
    void amountBelowTheLimitIsAccepted() {
        assertThat(gateway.charge(UUID.randomUUID(), new BigDecimal("49.99")).success()).isTrue();
    }

    @Test
    void amountExactlyAtTheLimitIsAccepted() {
        assertThat(gateway.charge(UUID.randomUUID(), new BigDecimal("1000.00")).success()).isTrue();
    }

    @Test
    void amountAboveTheLimitIsDeclinedWithAReason() {
        GatewayResult result = gateway.charge(UUID.randomUUID(), new BigDecimal("1000.01"));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("exceeds");
    }
}

