package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.ProductId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryOrderRepository implements OrderRepository {

  private final Map<OrderId, Order> orders = new LinkedHashMap<>();

  public static InMemoryOrderRepository withLocalFixtures() {
    InMemoryOrderRepository repository = new InMemoryOrderRepository();
    repository.save(Order.confirmed(
        new OrderId("alice-order"),
        "alice",
        new CartId("alice-cart"),
        // Canonical product TSID (was "starter-mug"); order/cart ids stay readable for SpiceDB.
        List.of(new OrderLine(new ProductId("6801HWW000000"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        "local-auth-alice-order",
        Instant.parse("2026-06-20T00:00:00Z")));
    return repository;
  }

  @Override
  public Optional<Order> findById(OrderId orderId) {
    return Optional.ofNullable(orders.get(orderId)).map(Order::copy);
  }

  @Override
  public Order save(Order order) {
    orders.put(order.id(), order.copy());
    return order.copy();
  }
}
