package com.example.commerce.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
        "AUTHORIZED",
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
        });
    assertThat(repository.findByIdempotencyKey("missing")).isEmpty();
  }
}
