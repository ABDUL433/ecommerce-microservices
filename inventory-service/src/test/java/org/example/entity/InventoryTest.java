package org.example.entity;

import org.example.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    private final UUID productId = UUID.randomUUID();

    @Test
    void reserve_movesStockFromAvailableToReserved() {
        Inventory inv = new Inventory(productId, 10);

        inv.reserve(3);

        assertThat(inv.getAvailableQty()).isEqualTo(7);
        assertThat(inv.getReservedQty()).isEqualTo(3);
    }

    @Test
    void reserve_moreThanAvailable_throwsAndLeavesStockUntouched() {
        Inventory inv = new Inventory(productId, 5);

        assertThatThrownBy(() -> inv.reserve(6)).isInstanceOf(InsufficientStockException.class);

        assertThat(inv.getAvailableQty()).isEqualTo(5);
        assertThat(inv.getReservedQty()).isZero();
    }

    @Test
    void release_movesReservedStockBack() {
        Inventory inv = new Inventory(productId, 10);
        inv.reserve(4);

        inv.release(4);

        assertThat(inv.getAvailableQty()).isEqualTo(10);
        assertThat(inv.getReservedQty()).isZero();
    }

    @Test
    void release_moreThanReserved_throws() {
        Inventory inv = new Inventory(productId, 10);
        inv.reserve(2);

        assertThatThrownBy(() -> inv.release(3)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commit_removesReservedStockForGood() {
        Inventory inv = new Inventory(productId, 10);
        inv.reserve(3);

        inv.commit(3);

        assertThat(inv.getAvailableQty()).isEqualTo(7);
        assertThat(inv.getReservedQty()).isZero();
    }

    @Test
    void nonPositiveQuantitiesAreRejected() {
        Inventory inv = new Inventory(productId, 10);

        assertThatThrownBy(() -> inv.reserve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inv.reserve(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inv.addStock(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeInitialStockIsRejected() {
        assertThatThrownBy(() -> new Inventory(productId, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}

