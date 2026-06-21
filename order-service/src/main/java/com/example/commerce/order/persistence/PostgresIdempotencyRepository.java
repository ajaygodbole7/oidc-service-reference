package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.IdempotencyRecord;
import com.example.commerce.order.service.IdempotencyRepository;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres-backed checkout idempotency. The unique {@code (subject, idempotency_key)}
 * constraint is the atomic claim gate; no read-then-write shortcut decides ownership.
 */
// Non-final so Spring can create the CGLIB proxy for @Transactional(REQUIRES_NEW) on claim().
public class PostgresIdempotencyRepository implements IdempotencyRepository {

  private final OrderIdempotencyRowRepository rows;
  private final JdbcAggregateTemplate aggregateTemplate;

  public PostgresIdempotencyRepository(
      OrderIdempotencyRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    this.rows = rows;
    this.aggregateTemplate = aggregateTemplate;
  }

  @Override
  public Optional<IdempotencyRecord> find(String subject, IdempotencyKey key) {
    return rows.findBySubjectAndIdempotencyKey(subject, key.value())
        .map(PostgresIdempotencyRepository::toDomain);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean claim(String subject, IdempotencyKey key, String requestFingerprint, OrderId reservedOrderId) {
    try {
      aggregateTemplate.insert(new OrderIdempotencyRow(
          null, subject, key.value(), requestFingerprint, reservedOrderId.value()));
      return true;
    } catch (DuplicateKeyException exception) {
      if (!isIdempotencyKeyConflict(exception)) {
        throw exception;
      }
      return false;
    }
  }

  @Override
  public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
    OrderIdempotencyRow existing = rows.findBySubjectAndIdempotencyKey(subject, key.value())
        .orElseThrow(() -> new IllegalStateException("idempotency record not claimed"));
    if (!orderId.value().equals(existing.orderId())) {
      throw new IllegalStateException("idempotency record linked to a different order");
    }
    aggregateTemplate.update(new OrderIdempotencyRow(
        existing.id(),
        existing.subject(),
        existing.idempotencyKey(),
        existing.requestFingerprint(),
        orderId.value()));
  }

  private static IdempotencyRecord toDomain(OrderIdempotencyRow row) {
    return new IdempotencyRecord(
        row.subject(),
        new IdempotencyKey(row.idempotencyKey()),
        row.requestFingerprint(),
        new OrderId(row.orderId()));
  }

  private static boolean isIdempotencyKeyConflict(DuplicateKeyException exception) {
    String message = String.valueOf(exception.getMostSpecificCause().getMessage());
    return message.contains("order_idempotency_subject_idempotency_key_key")
        || message.contains("order_idempotency_subject_idempotency_key");
  }
}
