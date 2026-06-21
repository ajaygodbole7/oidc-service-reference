package com.example.commerce.cart.persistence;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartItem;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.domain.Quantity;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link CartRepository}. The cart is an aggregate (root + item collection);
 * Spring Data JDBC replaces the child rows on each save.
 *
 * <p>Insert vs update is decided by the row's {@code @Version}, which is round-tripped through the
 * domain: {@link #findById}/{@link #findByOwnerSub} carry the persisted version onto the {@link Cart},
 * and {@link #save} writes it straight back with no re-read. A fresh cart has a null version and
 * inserts; a loaded cart updates with {@code WHERE version = ?} sourced from the version the caller
 * read, which bumps the version and turns a concurrent stale update into an
 * {@code OptimisticLockingFailureException}. Because the version is the one the caller observed (not
 * re-fetched at write time), the lock actually bites through the domain API.
 */
public final class PostgresCartRepository implements CartRepository {

  private final CartRowRepository rows;
  private final JdbcAggregateTemplate aggregateTemplate;

  public PostgresCartRepository(CartRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    this.rows = rows;
    this.aggregateTemplate = aggregateTemplate;
  }

  @Override
  public Optional<Cart> findById(CartId cartId) {
    return rows.findById(cartId.value()).map(PostgresCartRepository::toDomain);
  }

  @Override
  public Optional<Cart> findByOwnerSub(String ownerSub) {
    return rows.findByOwnerSub(ownerSub).map(PostgresCartRepository::toDomain);
  }

  @Override
  public Cart save(Cart cart) {
    aggregateTemplate.save(toRow(cart, cart.version()));
    return cart;
  }

  private static CartRow toRow(Cart cart, @Nullable Long version) {
    List<CartItemRow> items = cart.items().stream()
        .map(item -> new CartItemRow(
            item.productId().value(),
            item.quantity().value(),
            item.unitPrice().amount(),
            item.unitPrice().currency()))
        .collect(Collectors.toList());
    return new CartRow(cart.id().value(), cart.ownerSub(), version, items);
  }

  private static Cart toDomain(CartRow row) {
    List<CartItem> items = row.items().stream()
        .map(item -> new CartItem(
            new ProductId(item.productId()),
            new Quantity(item.quantity()),
            new Money(item.unitPriceAmount(), item.unitPriceCurrency())))
        .collect(Collectors.toList());
    return new Cart(new CartId(row.id()), row.ownerSub(), items, row.version());
  }
}
