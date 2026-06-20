package com.example.commerce.order.persistence;

import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_idempotency")
record OrderIdempotencyRow(
    @Id @Nullable Long id,
    String subject,
    @Column("idempotency_key") String idempotencyKey,
    @Column("request_fingerprint") String requestFingerprint,
    @Column("order_id") @Nullable String orderId) {
}
