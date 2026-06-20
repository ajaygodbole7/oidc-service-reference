package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.IdempotencyRecord;
import com.example.commerce.order.service.IdempotencyRepository;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed checkout idempotency. The unique {@code (subject, idempotency_key)}
 * constraint is the atomic claim gate; no read-then-write shortcut decides ownership.
 */
public final class PostgresIdempotencyRepository implements IdempotencyRepository {

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
  public boolean claim(String subject, IdempotencyKey key, String requestFingerprint) {
    try {
      aggregateTemplate.insert(new OrderIdempotencyRow(
          null, subject, key.value(), requestFingerprint, null));
      return true;
    } catch (DuplicateKeyException exception) {
      return false;
    }
  }

  @Override
  public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
    OrderIdempotencyRow existing = rows.findBySubjectAndIdempotencyKey(subject, key.value())
        .orElseThrow(() -> new IllegalStateException("idempotency record not claimed"));
    aggregateTemplate.update(new OrderIdempotencyRow(
        existing.id(),
        existing.subject(),
        existing.idempotencyKey(),
        existing.requestFingerprint(),
        orderId.value()));
  }

  private static IdempotencyRecord toDomain(OrderIdempotencyRow row) {
    OrderId linkedOrderId = row.orderId() == null ? null : new OrderId(row.orderId());
    return new IdempotencyRecord(
        row.subject(),
        new IdempotencyKey(row.idempotencyKey()),
        row.requestFingerprint(),
        linkedOrderId);
  }
}
