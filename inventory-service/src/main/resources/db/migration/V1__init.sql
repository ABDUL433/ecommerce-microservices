CREATE TABLE product (
                         id          UUID PRIMARY KEY,
                         sku         VARCHAR(64)    NOT NULL UNIQUE,
                         name        VARCHAR(255)   NOT NULL,
                         description TEXT,
                         price       NUMERIC(12, 2) NOT NULL,
                         active      BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE inventory (
                           id            UUID PRIMARY KEY,
                           product_id    UUID    NOT NULL UNIQUE,
                           available_qty INTEGER NOT NULL CHECK (available_qty >= 0),
                           reserved_qty  INTEGER NOT NULL CHECK (reserved_qty >= 0),
                           version       BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE stock_reservation (
                                   id         UUID PRIMARY KEY,
                                   order_id   UUID        NOT NULL,
                                   product_id UUID        NOT NULL,
                                   qty        INTEGER     NOT NULL CHECK (qty > 0),
                                   status     VARCHAR(20) NOT NULL,
                                   CONSTRAINT uq_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_reservation_order ON stock_reservation (order_id);

CREATE TABLE outbox (
                        id           UUID PRIMARY KEY,
                        aggregate_id UUID         NOT NULL,
                        event_type   VARCHAR(100) NOT NULL,
                        payload      TEXT         NOT NULL,
                        published    BOOLEAN      NOT NULL DEFAULT FALSE,
                        created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at);