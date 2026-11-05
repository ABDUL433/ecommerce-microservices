package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** The customer is no longer part of the request: it is taken from the login token. */
public record CreateOrderRequest(
        @NotEmpty @Valid List<OrderItemRequest> items) {
}
