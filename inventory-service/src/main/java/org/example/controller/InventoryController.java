package org.example.controller;

import jakarta.validation.Valid;
import org.example.dto.AddStockRequest;
import org.example.dto.InventoryResponse;
import org.example.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable("productId") UUID productId) {
        return inventoryService.getInventory(productId);
    }

    @PostMapping("/{productId}/stock")
    public InventoryResponse addStock(@PathVariable("productId") UUID productId,
                                      @Valid @RequestBody AddStockRequest request) {
        return inventoryService.addStock(productId, request.qty());
    }
}