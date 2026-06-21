package com.example.commerce.payment.persistence;

import com.example.commerce.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Spring Data JDBC row for {@code payment_authorizations}; kept separate from the domain record. */
@Table("payment_authorizations")
record PaymentRow(
    @Id @Column("payment_id") String paymentId,
    @Version @Column("version") @Nullable Long version,
    @Column("order_id") String orderId,
    @Column("user_sub") String userSub,
    @Column("amount_amount") BigDecimal amountAmount,
    @Column("amount_currency") String amountCurrency,
    PaymentStatus status,
    @Column("idempotency_key") String idempotencyKey,
    @Column("command_fingerprint") String commandFingerprint,
    @Column("authorized_at") Instant authorizedAt) {
}
