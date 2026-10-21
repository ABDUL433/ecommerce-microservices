CREATE TABLE payment (
    id             UUID PRIMARY KEY,
    order_id       UUID           NOT NULL UNIQUE,
    customer_id    UUID           NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ    NOT NULL
);

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at);
