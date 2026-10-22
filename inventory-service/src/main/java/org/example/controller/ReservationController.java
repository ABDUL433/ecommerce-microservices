package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.ReservationResponse;
import org.example.dto.ReserveRequest;
import org.example.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for testing the reservation logic with Postman.
 * In the final design the same service methods are called by Kafka consumers.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final InventoryService inventoryService;

    public ReservationController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request.orderId(), request.productId(), request.qty());
    }

    @GetMapping("/{orderId}")
    public List<ReservationResponse> get(@PathVariable("orderId") UUID orderId) {
        return inventoryService.getReservations(orderId);
    }

    @PostMapping("/{orderId}/release")
    public List<ReservationResponse> release(@PathVariable("orderId") UUID orderId) {
        return inventoryService.release(orderId);
    }

    @PostMapping("/{orderId}/commit")
    public List<ReservationResponse> commit(@PathVariable("orderId") UUID orderId) {
        return inventoryService.commit(orderId);
    }
}
