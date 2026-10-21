package org.example.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/** Abstraction over the external payment provider, so the fake can be swapped for a real one. */
public interface PaymentGateway {

    GatewayResult charge(UUID orderId, BigDecimal amount);
}
