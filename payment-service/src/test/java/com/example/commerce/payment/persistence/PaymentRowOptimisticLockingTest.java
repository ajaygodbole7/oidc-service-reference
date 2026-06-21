package com.example.commerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Proves the {@code @Version} column on {@link PaymentRow} gives Spring Data JDBC optimistic
 * locking: a second update built from the original (stale) version is rejected with
 * {@link OptimisticLockingFailureException} once the first update has bumped the row version.
 * This replaces the old {@code existsById}-then-insert/update TOCTOU.
 */
@Testcontainers
@SpringBootTest
class PaymentRowOptimisticLockingTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private JdbcAggregateTemplate aggregateTemplate;

  @Test
  void staleVersionUpdateIsRejected() {
    // INSERT: null @Version -> Spring Data assigns the initial version (0) and inserts.
    PaymentRow inserted = aggregateTemplate.save(new PaymentRow(
        "pay-lock-1",
        null,
        "order-lock-1",
        "alice",
        new BigDecimal("12.50"),
        "USD",
        PaymentStatus.AUTHORIZED,
        "idem-lock-1",
        "fingerprint-lock-1",
        Instant.parse("2026-01-01T00:00:00Z")));
    assertThat(inserted.version()).isNotNull();

    // First writer wins: a real UPDATE bumps the persisted version.
    PaymentRow firstUpdate = aggregateTemplate.save(
        withStatus(inserted, PaymentStatus.DECLINED));
    assertThat(firstUpdate.version()).isGreaterThan(inserted.version());

    // Second writer holds the stale (pre-bump) version -> optimistic-lock failure.
    PaymentRow staleUpdate = withStatus(inserted, PaymentStatus.AUTHORIZED);
    assertThatThrownBy(() -> aggregateTemplate.save(staleUpdate))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  private static PaymentRow withStatus(PaymentRow row, PaymentStatus status) {
    return new PaymentRow(
        row.paymentId(),
        row.version(),
        row.orderId(),
        row.userSub(),
        row.amountAmount(),
        row.amountCurrency(),
        status,
        row.idempotencyKey(),
        row.commandFingerprint(),
        row.authorizedAt());
  }
}
