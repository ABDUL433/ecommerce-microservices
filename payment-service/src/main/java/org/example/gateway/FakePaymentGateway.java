package org.example.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Declines any payment above payment.gateway.max-amount (default 1000). Everything else succeeds. */
@Component
public class FakePaymentGateway implements PaymentGateway {

    private final BigDecimal maxAmount;

    public FakePaymentGateway(@Value("${payment.gateway.max-amount:1000}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    @Override
    public GatewayResult charge(UUID orderId, BigDecimal amount) {
        if (amount.compareTo(maxAmount) > 0) {
            return GatewayResult.declined("Amount " + amount + " exceeds the limit of " + maxAmount);
        }
        return GatewayResult.ok();
    }
}
