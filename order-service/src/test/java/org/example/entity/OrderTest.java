package org.example.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order newOrder() {
        return new Order(UUID.randomUUID());
    }

    @Test
    void newOrderIsPending_andTotalIsTheSumOfItsLines() {
        Order order = newOrder();

        order.addItem(UUID.randomUUID(), 2, new BigDecimal("49.99"));
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("109.98");
    }

    @Test
    void confirm_isRepeatable() {
        Order order = newOrder();

        order.confirm();
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancel_isRepeatable() {
        Order order = newOrder();

        order.cancel();
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void confirmedOrderCannotBeCancelled() {
        Order order = newOrder();
        order.confirm();

        assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelledOrderCannotBeConfirmed() {
        Order order = newOrder();
        order.cancel();

        assertThatThrownBy(order::confirm).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}

