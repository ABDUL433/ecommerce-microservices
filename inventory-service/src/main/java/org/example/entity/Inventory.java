package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.example.exception.InsufficientStockException;

import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    // Hibernate checks and increments this on every update (optimistic locking)
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Inventory() {
        // required by JPA
    }

    public Inventory(UUID productId, int availableQty) {
        if (availableQty < 0) {
            throw new IllegalArgumentException("availableQty must be >= 0");
        }
        this.productId = productId;
        this.availableQty = availableQty;
        this.reservedQty = 0;
    }

    /** Move stock from available to reserved. Used when an order is created. */
    public void reserve(int qty) {
        requirePositive(qty);
        if (availableQty < qty) {
            throw new InsufficientStockException(productId, qty, availableQty);
        }
        availableQty -= qty;
        reservedQty += qty;
    }

    /** Give reserved stock back. Saga compensation when payment fails. */
    public void release(int qty) {
        requirePositive(qty);
        if (reservedQty < qty) {
            throw new IllegalStateException(
                    "Cannot release " + qty + " for product " + productId + ", only " + reservedQty + " reserved");
        }
        reservedQty -= qty;
        availableQty += qty;
    }

    /** Stock leaves permanently. Used after payment succeeds. */
    public void commit(int qty) {
        requirePositive(qty);
        if (reservedQty < qty) {
            throw new IllegalStateException(
                    "Cannot commit " + qty + " for product " + productId + ", only " + reservedQty + " reserved");
        }
        reservedQty -= qty;
    }

    /** Add new stock (restock). */
    public void addStock(int qty) {
        requirePositive(qty);
        availableQty += qty;
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailableQty() {
        return availableQty;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public long getVersion() {
        return version;
    }
}
