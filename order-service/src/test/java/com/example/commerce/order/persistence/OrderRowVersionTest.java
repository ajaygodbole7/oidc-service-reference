package com.example.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.ProductId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the {@code orders.version} optimistic lock against real Postgres: a fresh aggregate
 * inserts at version 0, a re-persist bumps the version, an update carrying a STALE version fails the
 * lock, and the recover-forward convergence in {@link PostgresOrderRepository#save(Order)} still
 * works (a re-persist of an unchanged order does NOT spuriously fail). WRITE-only here — the
 * orchestrator runs Testcontainers separately.
 */
@Testcontainers
@SpringBootTest
@Transactional
class OrderRowVersionTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private OrderRowRepository rows;

  @Autowired
  private JdbcAggregateTemplate aggregateTemplate;

  @Test
  void freshInsertStartsAtVersionZeroAndRepersistBumpsIt() {
    orderRepository.save(order("ver-order-1", "auth-1"));
    assertThat(rows.findById("ver-order-1").orElseThrow().version()).isEqualTo(0L);

    // Recover-forward re-persist (same id, unchanged caller) must converge, not fail the lock.
    orderRepository.save(order("ver-order-1", "auth-1"));
    assertThat(rows.findById("ver-order-1").orElseThrow().version()).isEqualTo(1L);
  }

  @Test
  void updateWithStaleVersionFailsTheOptimisticLock() {
    orderRepository.save(order("ver-order-2", "auth-2"));
    OrderRow current = rows.findById("ver-order-2").orElseThrow();
    assertThat(current.version()).isEqualTo(0L);

    // Bump the row once so the stored version moves to 1.
    aggregateTemplate.update(withVersion(current, 0L));
    assertThat(rows.findById("ver-order-2").orElseThrow().version()).isEqualTo(1L);

    // A second update that still believes the version is 0 must be rejected by the lock.
    assertThatThrownBy(() -> aggregateTemplate.update(withVersion(current, 0L)))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void concurrentRecoveryInsertConvergesViaSaveWithoutLosingTheRow() {
    // Simulate the loser of a concurrent reserved-id insert: the row already exists, a second
    // save must converge onto it (update with the stored version) rather than throw.
    orderRepository.save(order("ver-order-3", "auth-3a"));
    orderRepository.save(order("ver-order-3", "auth-3a"));

    Order reloaded = orderRepository.findById(new OrderId("ver-order-3")).orElseThrow();
    assertThat(reloaded.paymentAuthorizationId()).isEqualTo("auth-3a");
    assertThat(rows.findById("ver-order-3").orElseThrow().version()).isEqualTo(1L);
  }

  private static Order order(String id, String authId) {
    return Order.confirmed(
        new OrderId(id),
        "alice",
        new CartId("alice-cart"),
        List.of(new OrderLine(new ProductId("6801HWW000000"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        authId,
        Instant.parse("2026-06-20T00:00:00Z"));
  }

  private static OrderRow withVersion(OrderRow row, Long version) {
    return new OrderRow(
        row.id(),
        version,
        row.ownerSub(),
        row.sourceCartId(),
        row.totalAmount(),
        row.totalCurrency(),
        row.paymentAuthorizationId(),
        row.createdAt(),
        row.status(),
        row.lines().stream()
            .map(line -> new OrderLineRow(
                line.productId(),
                line.quantity(),
                new BigDecimal(line.unitPriceAmount().toPlainString()),
                line.unitPriceCurrency()))
            .toList());
  }
}
