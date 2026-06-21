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
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link CartRepository}. The cart is an aggregate (root + item collection);
 * Spring Data JDBC replaces the child rows on each save. The assigned cart id drives insert vs update.
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
    CartRow row = toRow(cart);
    if (rows.existsById(cart.id().value())) {
      aggregateTemplate.update(row);
    } else {
      aggregateTemplate.insert(row);
    }
    return cart;
  }

  private static CartRow toRow(Cart cart) {
    List<CartItemRow> items = cart.items().stream()
        .map(item -> new CartItemRow(
            item.productId().value(),
            item.quantity().value(),
            item.unitPrice().amount(),
            item.unitPrice().currency()))
        .collect(Collectors.toList());
    return new CartRow(cart.id().value(), cart.ownerSub(), items);
  }

  private static Cart toDomain(CartRow row) {
    List<CartItem> items = row.items().stream()
        .map(item -> new CartItem(
            new ProductId(item.productId()),
            new Quantity(item.quantity()),
            new Money(item.unitPriceAmount(), item.unitPriceCurrency())))
        .collect(Collectors.toList());
    return new Cart(new CartId(row.id()), row.ownerSub(), items);
  }
}
