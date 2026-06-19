package com.example.commerce.cart.persistence;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCartRepository implements CartRepository {

  private final Map<CartId, Cart> carts = new ConcurrentHashMap<>();

  public InMemoryCartRepository(List<Cart> initialCarts) {
    initialCarts.forEach(this::save);
  }

  public static InMemoryCartRepository withLocalFixtures() {
    return new InMemoryCartRepository(List.of(
        new Cart(new CartId("alice-cart"), "alice", List.of()),
        new Cart(new CartId("bob-cart"), "bob", List.of())));
  }

  @Override
  public Optional<Cart> findById(CartId cartId) {
    return Optional.ofNullable(carts.get(cartId)).map(Cart::copy);
  }

  @Override
  public Optional<Cart> findByOwnerSub(String ownerSub) {
    return carts.values().stream()
        .filter(cart -> cart.ownerSub().equals(ownerSub))
        .findFirst()
        .map(Cart::copy);
  }

  @Override
  public Cart save(Cart cart) {
    Cart stored = cart.copy();
    carts.put(stored.id(), stored);
    return stored.copy();
  }
}
