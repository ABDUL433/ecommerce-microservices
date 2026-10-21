package org.example.gateway;

public record GatewayResult(boolean success, String failureReason) {

    public static GatewayResult ok() {
        return new GatewayResult(true, null);
    }

    public static GatewayResult declined(String reason) {
        return new GatewayResult(false, reason);
    }
}
