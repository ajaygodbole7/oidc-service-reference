package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.OrderStatus;
import com.example.commerce.order.domain.ProductId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link OrderRepository}. The order aggregate root owns its line collection.
 */
public final class PostgresOrderRepository implements OrderRepository {

  private final OrderRowRepository rows;
  private final JdbcAggregateTemplate aggregateTemplate;

  public PostgresOrderRepository(OrderRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    this.rows = rows;
    this.aggregateTemplate = aggregateTemplate;
  }

  @Override
  public Optional<Order> findById(OrderId orderId) {
    return rows.findById(orderId.value()).map(PostgresOrderRepository::toDomain);
  }

  @Override
  public Order save(Order order) {
    OrderRow row = toRow(order);
    if (rows.existsById(order.id().value())) {
      aggregateTemplate.update(row);
    } else {
      aggregateTemplate.insert(row);
    }
    return order.copy();
  }

  private static OrderRow toRow(Order order) {
    Set<OrderLineRow> lines = order.lines().stream()
        .map(line -> new OrderLineRow(
            line.productId().value(),
            line.quantity(),
            line.unitPrice().amount(),
            line.unitPrice().currency()))
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    return new OrderRow(
        order.id().value(),
        order.ownerSub(),
        order.sourceCartId().value(),
        order.total().amount(),
        order.total().currency(),
        order.paymentAuthorizationId(),
        order.createdAt(),
        order.status().name(),
        lines);
  }

  private static Order toDomain(OrderRow row) {
    List<OrderLine> lines = row.lines().stream()
        .map(line -> new OrderLine(
            new ProductId(line.productId()),
            line.quantity(),
            new Money(line.unitPriceAmount(), line.unitPriceCurrency())))
        .collect(Collectors.toList());
    return new Order(
        new OrderId(row.id()),
        row.ownerSub(),
        new CartId(row.sourceCartId()),
        lines,
        new Money(row.totalAmount(), row.totalCurrency()),
        row.paymentAuthorizationId(),
        row.createdAt(),
        OrderStatus.valueOf(row.status()));
  }
}
