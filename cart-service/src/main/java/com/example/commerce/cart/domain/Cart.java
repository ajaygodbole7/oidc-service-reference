package com.example.commerce.cart.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class Cart {

  private final CartId id;
  private final String ownerSub;
  private final List<CartItem> items;
  private final @Nullable Long version;

  /** A new cart (no persisted row yet): version is null so the first save inserts. */
  public Cart(CartId id, String ownerSub, List<CartItem> items) {
    this(id, ownerSub, items, null);
  }

  /** A loaded cart: version is the persisted optimistic-lock version that drives the next update. */
  public Cart(CartId id, String ownerSub, List<CartItem> items, @Nullable Long version) {
    this.id = Objects.requireNonNull(id, "id");
    if (ownerSub == null || ownerSub.isBlank()) {
      throw new IllegalArgumentException("ownerSub is required");
    }
    this.ownerSub = ownerSub;
    this.items = new ArrayList<>(Objects.requireNonNull(items, "items"));
    this.version = version;
  }

  public CartId id() {
    return id;
  }

  public String ownerSub() {
    return ownerSub;
  }

  /** Persisted optimistic-lock version, or null for a cart that has not been saved yet. */
  public @Nullable Long version() {
    return version;
  }

  public List<CartItem> items() {
    return Collections.unmodifiableList(items);
  }

  public Money total() {
    return items.stream()
        .map(CartItem::lineTotal)
        .reduce(Money::plus)
        .orElse(Money.ZERO);
  }

  public void addItem(ProductId productId, Quantity quantity, Money unitPrice) {
    Optional<CartItem> existing = items.stream()
        .filter(item -> item.productId().equals(productId))
        .findFirst();
    if (existing.isPresent()) {
      CartItem current = existing.get();
      items.remove(current);
      items.add(current.withAdditionalQuantity(quantity));
      return;
    }
    items.add(new CartItem(productId, quantity, unitPrice));
  }

  public void removeItem(ProductId productId) {
    boolean removed = items.removeIf(item -> item.productId().equals(productId));
    if (!removed) {
      throw new IllegalArgumentException("cart item does not exist: " + productId.value());
    }
  }

  public Cart copy() {
    return new Cart(id, ownerSub, items, version);
  }
}
