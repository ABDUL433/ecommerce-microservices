package org.example.repository;

import org.example.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    boolean existsByOrderId(UUID orderId);

    Optional<Payment> findByOrderId(UUID orderId);
}
