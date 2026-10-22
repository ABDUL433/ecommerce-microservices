package org.example.dto;

import jakarta.validation.constraints.Min;

public record AddStockRequest(@Min(1) int qty) {
}
