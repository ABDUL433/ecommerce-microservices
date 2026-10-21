package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.CreateOrderRequest;
import org.example.dto.OrderItemRequest;
import org.example.dto.OrderResponse;
import org.example.entity.Order;
import org.example.entity.OrderStatus;
import org.example.entity.Outbox;
import org.example.exception.ResourceNotFoundException;
import org.example.messaging.OrderCreatedEvent;
import org.example.messaging.PaymentRequestedEvent;
import org.example.repository.OrderRepository;
import org.example.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves the order as PENDING and an OrderCreated outbox row in the SAME transaction,
     * so an order can never exist without its event (and the other way round).
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order(request.customerId());
        for (OrderItemRequest item : request.items()) {
            order.addItem(item.productId(), item.quantity(), item.unitPrice());
        }
        Order saved = orderRepository.save(order);
        outboxRepository.save(new Outbox(saved.getId(), OrderCreatedEvent.TYPE,
                toJson(OrderCreatedEvent.from(saved))));
        return OrderResponse.from(saved);
    }

    /**
     * Stock is reserved: ask the Payment Service to charge the order total.
     * A repeated StockReserved event writes a second request, which the Payment Service ignores.
     * Throws IllegalStateException if the order is no longer PENDING.
     */
    @Transactional
    public void requestPayment(UUID orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Order " + orderId + " is " + order.getStatus() + ", payment not requested");
        }
        outboxRepository.save(new Outbox(orderId, PaymentRequestedEvent.TYPE,
                toJson(PaymentRequestedEvent.of(order))));
    }

    /** Saga success: payment succeeded. Repeating it is harmless. */
    @Transactional
    public void confirmOrder(UUID orderId) {
        findOrder(orderId).confirm();
    }

    /**
     * Saga failure: marks the order CANCELLED. Repeating it is harmless.
     * Throws IllegalStateException if the order was already confirmed.
     * A concurrent update raises an optimistic-lock error and Kafka redelivers the event.
     */
    @Transactional
    public void cancelOrder(UUID orderId) {
        findOrder(orderId).cancel();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        return OrderResponse.from(findOrder(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(OrderResponse::from).toList();
    }

    private Order findOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
