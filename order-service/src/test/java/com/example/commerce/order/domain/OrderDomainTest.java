package com.example.commerce.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

  @Test
  void confirmed_order_requires_at_least_one_line() {
    assertThatThrownBy(() -> Order.confirmed(
            new OrderId("order-1"),
            "alice",
            new CartId("alice-cart"),
            List.of(),
            Money.usd("0.00"),
            "auth-1",
            Instant.parse("2026-06-20T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one line");
  }

  @Test
  void line_total_multiplies_unit_price_by_quantity() {
    OrderLine line = new OrderLine(new ProductId("starter-mug"), 3, Money.usd("12.50"));

    assertThat(line.lineTotal()).isEqualTo(Money.usd("37.50"));
  }

  @Test
  void cancel_changes_status_once_and_rejects_duplicate_cancel() {
    Order order = order();

    order.cancel();

    assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    assertThatThrownBy(order::cancel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already cancelled");
  }

  private static Order order() {
    return Order.confirmed(
        new OrderId("order-1"),
        "alice",
        new CartId("alice-cart"),
        List.of(new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        "auth-1",
        Instant.parse("2026-06-20T00:00:00Z"));
  }
}
