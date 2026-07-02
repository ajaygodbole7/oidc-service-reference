package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.OrderStatus;
import com.example.commerce.order.domain.ProductId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link OrderRepository}. The order aggregate root owns its line collection.
 *
 * <p>The domain {@link Order} deliberately does NOT carry a version (it is a persistence concern), so
 * {@link #save(Order)} decides INSERT vs UPDATE and, on UPDATE, reloads the stored version first and
 * writes onto it. This is a reload-converge save, not an optimistic-conflict guard: because the
 * version is re-read at write time, a re-persist always converges onto the current row. That is the
 * correct shape for the recover-forward path here — checkout writes the order once and the only
 * mutation, cancel, is idempotent — so there is no separate caller-mutation path that needs to fail
 * the lock. The {@code orders.version} column is still bumped on each UPDATE and is available should a
 * future concurrent-mutation path need to thread a caller-observed version through the domain.
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
  public List<Order> findPageByIdsDesc(Collection<String> allowedIds, @Nullable String afterId, int limit) {
    if (allowedIds.isEmpty()) {
      return List.of();
    }
    return rows.findPageByIdsDesc(allowedIds, afterId, limit).stream()
        .map(PostgresOrderRepository::toDomain)
        .toList();
  }

  @Override
  public Order save(Order order) {
    Optional<OrderRow> existing = rows.findById(order.id().value());
    if (existing.isPresent()) {
      // Re-persist: carry the stored version so the optimistic UPDATE asserts-and-bumps it.
      updateWithStoredVersion(order, existing.get().version());
    } else {
      try {
        aggregateTemplate.insert(toRow(order, null));
      } catch (DuplicateKeyException concurrentInsert) {
        // A concurrent recovery for the same reserved order id won the insert; reload its version
        // and converge onto it rather than blindly re-inserting.
        Long version = rows.findById(order.id().value())
            .map(OrderRow::version)
            .orElseThrow(() -> concurrentInsert);
        updateWithStoredVersion(order, version);
      }
    }
    return order.copy();
  }

  private void updateWithStoredVersion(Order order, @Nullable Long storedVersion) {
    aggregateTemplate.update(toRow(order, storedVersion));
  }

  private static OrderRow toRow(Order order, @Nullable Long version) {
    List<OrderLineRow> lines = order.lines().stream()
        .map(line -> new OrderLineRow(
            line.productId().value(),
            line.quantity(),
            line.unitPrice().amount(),
            line.unitPrice().currency()))
        .collect(Collectors.toList());
    return new OrderRow(
        order.id().value(),
        version,
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
