package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.ProductId;
import com.example.commerce.order.service.CartLookup;
import com.example.commerce.order.service.CartSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemoryCartLookup implements CartLookup {

  private final Map<String, CartSnapshot> cartsBySubject = new LinkedHashMap<>();

  public static InMemoryCartLookup withLocalFixtures() {
    InMemoryCartLookup lookup = new InMemoryCartLookup();
    lookup.put("alice", new CartSnapshot(new CartId("alice-cart"), List.of(
        new OrderLine(new ProductId("starter-mug"), 2, Money.usd("12.50")))));
    lookup.put("bob", new CartSnapshot(new CartId("bob-cart"), List.of(
        new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50")))));
    return lookup;
  }

  public void put(String subject, CartSnapshot cart) {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    cartsBySubject.put(subject, cart);
  }

  @Override
  public Optional<CartSnapshot> findCurrentCartForSubject(String subject) {
    return Optional.ofNullable(cartsBySubject.get(subject));
  }
}
