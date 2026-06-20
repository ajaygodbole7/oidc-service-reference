package com.example.commerce.payment.persistence;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link PaymentAuthorizationRepository}. The {@code idempotency_key} unique
 * constraint enforces payment-side dedup; the assigned {@code payment_id} drives insert vs update.
 */
public final class PostgresPaymentAuthorizationRepository implements PaymentAuthorizationRepository {

  private final PaymentAuthorizationRowRepository rows;
  private final JdbcAggregateTemplate aggregateTemplate;

  public PostgresPaymentAuthorizationRepository(
      PaymentAuthorizationRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    this.rows = rows;
    this.aggregateTemplate = aggregateTemplate;
  }

  @Override
  public Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey) {
    return rows.findByIdempotencyKey(idempotencyKey).map(PostgresPaymentAuthorizationRepository::toDomain);
  }

  @Override
  public PaymentAuthorization save(PaymentAuthorization authorization) {
    PaymentRow row = toRow(authorization);
    if (rows.existsById(authorization.paymentId())) {
      aggregateTemplate.update(row);
    } else {
      aggregateTemplate.insert(row);
    }
    return authorization;
  }

  private static PaymentRow toRow(PaymentAuthorization a) {
    return new PaymentRow(
        a.paymentId(),
        a.orderId().value(),
        a.userSub(),
        a.amount().amount(),
        a.amount().currency(),
        a.status(),
        a.idempotencyKey(),
        a.commandFingerprint(),
        a.authorizedAt());
  }

  private static PaymentAuthorization toDomain(PaymentRow r) {
    return new PaymentAuthorization(
        r.paymentId(),
        new OrderId(r.orderId()),
        r.userSub(),
        new Money(r.amountAmount(), r.amountCurrency()),
        r.status(),
        r.idempotencyKey(),
        r.commandFingerprint(),
        r.authorizedAt());
  }
}
