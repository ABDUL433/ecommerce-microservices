package org.example.service;

import java.util.UUID;

public record ReservationLine(UUID productId, int quantity) {
}

