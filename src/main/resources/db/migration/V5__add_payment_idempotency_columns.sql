alter table payments
    add column idempotency_key varchar(100),
    add column request_hash varchar(64);

alter table payments
    add constraint uk_payments_idempotency_key unique (idempotency_key);
