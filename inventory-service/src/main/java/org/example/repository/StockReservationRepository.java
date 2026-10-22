package org.example.repository;

import org.example.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    List<StockReservation> findByOrderId(UUID orderId);
}
