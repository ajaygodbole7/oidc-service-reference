package com.example.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.OrderStatus;
import com.example.commerce.order.domain.ProductId;
import com.example.commerce.order.service.IdempotencyRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies order-service's Postgres-backed aggregate and idempotency claim/link contract
 * against real Postgres, including Flyway seed parity with the old in-memory fixture.
 */
@Testcontainers
@SpringBootTest
@Transactional
// "test" is in the SecretSentinelGuard local-profile allow-list, so the committed dev-default
// secrets downgrade to a WARN instead of failing the context boot.
@ActiveProfiles("test")
class PostgresOrderRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private IdempotencyRepository idempotencyRepository;

  @Test
  void readsFlywaySeededOrderAndPersistsStatusChanges() {
    Order seeded = orderRepository.findById(new OrderId("alice-order")).orElseThrow();

    assertThat(seeded.ownerSub()).isEqualTo("alice");
    assertThat(seeded.lines()).hasSize(1);
    assertThat(seeded.total().amount()).isEqualByComparingTo("12.50");

    seeded.cancel();
    orderRepository.save(seeded);

    assertThat(orderRepository.findById(new OrderId("alice-order")).orElseThrow().status())
        .isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void savesOrderAggregateWithLines() {
    Order order = Order.confirmed(
        new OrderId("order-1"),
        "alice",
        new CartId("alice-cart"),
        List.of(
            new OrderLine(new ProductId("starter-mug"), 2, Money.usd("12.50")),
            new OrderLine(new ProductId("travel-bag"), 1, Money.usd("48.00"))),
        Money.usd("73.00"),
        "auth-order-1",
        Instant.parse("2026-06-20T01:00:00Z"));

    orderRepository.save(order);

    Order reloaded = orderRepository.findById(new OrderId("order-1")).orElseThrow();
    assertThat(reloaded.lines()).hasSize(2);
    assertThat(reloaded.lines())
        .extracting(line -> line.productId().value())
        .containsExactly("starter-mug", "travel-bag");
    assertThat(reloaded.total().amount()).isEqualByComparingTo("73.00");
    assertThat(reloaded.paymentAuthorizationId()).isEqualTo("auth-order-1");
  }

  @Test
  void claimsIdempotencyOnceThenLinksToOrder() {
    IdempotencyKey key = new IdempotencyKey("idem-1");

    assertThat(idempotencyRepository.claim("alice", key, "fingerprint-1", new OrderId("order-idem-1"))).isTrue();
    assertThat(idempotencyRepository.claim("alice", key, "fingerprint-1", new OrderId("order-idem-1"))).isFalse();
    assertThat(idempotencyRepository.claim("bob", key, "fingerprint-1", new OrderId("bob-order-idem-1"))).isTrue();
    assertThat(idempotencyRepository.find("alice", key)).get()
        .satisfies(record -> {
          assertThat(record.requestFingerprint()).isEqualTo("fingerprint-1");
          assertThat(record.orderId()).isEqualTo(new OrderId("order-idem-1"));
        });

    Order order = Order.confirmed(
        new OrderId("order-idem-1"),
        "alice",
        new CartId("alice-cart"),
        List.of(new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        "auth-order-idem-1",
        Instant.parse("2026-06-20T02:00:00Z"));
    orderRepository.save(order);
    idempotencyRepository.linkOrder("alice", key, order.id());

    assertThat(idempotencyRepository.find("alice", key)).get()
        .extracting(record -> record.orderId().value())
        .isEqualTo("order-idem-1");
  }

  @Test
  void linkingADifferentOrderThanWasReservedThrows() {
    IdempotencyKey key = new IdempotencyKey("idem-mismatch");
    // The claim reserves order-reserved; the unique (subject, key) row records that id.
    assertThat(idempotencyRepository.claim("alice", key, "fingerprint-mismatch", new OrderId("order-reserved")))
        .isTrue();

    // Linking a DIFFERENT order id than the one reserved must fail closed rather than silently
    // overwriting the reservation: the recover-forward path must keep the same reserved id.
    assertThatThrownBy(() -> idempotencyRepository.linkOrder("alice", key, new OrderId("order-other")))
        .isInstanceOf(IllegalStateException.class);

    // The reservation is unchanged after the rejected link.
    assertThat(idempotencyRepository.find("alice", key)).get()
        .extracting(record -> record.orderId().value())
        .isEqualTo("order-reserved");
  }
}
