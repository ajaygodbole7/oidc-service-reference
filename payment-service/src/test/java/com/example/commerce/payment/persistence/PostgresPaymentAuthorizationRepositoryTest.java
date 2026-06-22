package com.example.commerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the Postgres-backed payment repository against a real Postgres (save + dedup lookup by
 * idempotency key). Loading the full context also guards payment-service's bean wiring.
 */
@Testcontainers
@SpringBootTest
// "test" is in the SecretSentinelGuard local-profile allow-list, so the committed dev-default
// secrets downgrade to a WARN instead of failing the context boot.
@ActiveProfiles("test")
class PostgresPaymentAuthorizationRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private PaymentAuthorizationRepository repository;

  @Test
  void savesAndLooksUpByIdempotencyKey() {
    PaymentAuthorization authorization = new PaymentAuthorization(
        "pay-1",
        new OrderId("order-1"),
        "alice",
        new Money(new BigDecimal("12.50"), "USD"),
        PaymentStatus.AUTHORIZED,
        "idem-1",
        "fingerprint-1",
        Instant.parse("2026-01-01T00:00:00Z"));
    repository.save(authorization);

    assertThat(repository.findByIdempotencyKey("idem-1"))
        .get()
        .satisfies(found -> {
          assertThat(found.paymentId()).isEqualTo("pay-1");
          assertThat(found.amount().amount()).isEqualByComparingTo("12.50");
          assertThat(found.orderId().value()).isEqualTo("order-1");
          assertThat(found.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        });
    assertThat(repository.findByIdempotencyKey("missing")).isEmpty();
  }

  @Test
  void findByIdempotencyKeyReturnsEmptyOptionalForAnUnknownKey() {
    assertThat(repository.findByIdempotencyKey("no-such-key")).isEmpty();
  }

  @Test
  void duplicateIdempotencyKeyInsertViolatesTheUniqueConstraint() {
    // First authorization claims idempotency key "idem-dup".
    repository.save(authorization("pay-dup-1", "idem-dup"));

    // A second authorization with a DIFFERENT payment id but the SAME idempotency key is a
    // distinct INSERT (own primary key), so it reaches the real UNIQUE(idempotency_key) constraint
    // in Postgres and must surface as a DuplicateKeyException rather than a second authorization.
    // This is the #5 concurrent-create-replay path proven at the database level.
    assertThatThrownBy(() -> repository.save(authorization("pay-dup-2", "idem-dup")))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private static PaymentAuthorization authorization(String paymentId, String idempotencyKey) {
    return new PaymentAuthorization(
        paymentId,
        new OrderId("order-" + paymentId),
        "alice",
        new Money(new BigDecimal("12.50"), "USD"),
        PaymentStatus.AUTHORIZED,
        idempotencyKey,
        "fingerprint-" + paymentId,
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
