package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderResponse;
import org.example.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(customerId(jwt), request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    /** A customer sees only their own orders (another customer's order looks like "not found"). Admins see all. */
    @GetMapping("/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        return orderService.getOrder(id, customerId(jwt), isAdmin(jwt));
    }

    /** The logged-in customer's own orders, newest first. */
    @GetMapping
    public List<OrderResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return orderService.listOrders(customerId(jwt));
    }

    private static UUID customerId(Jwt jwt) {
        String claim = jwt.getClaimAsString("customerId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The token has no customerId");
        }
        try {
            return UUID.fromString(claim);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The token has an invalid customerId");
        }
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
