package com.example.commerce.order.service;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderLine;
import java.util.List;
import java.util.Objects;

public record CartSnapshot(CartId id, List<OrderLine> lines) {

  public CartSnapshot {
    Objects.requireNonNull(id, "id");
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("cart must contain at least one line");
    }
    lines = List.copyOf(lines);
  }

  public Money total() {
    return lines.stream()
        .map(OrderLine::lineTotal)
        .reduce(Money::plus)
        .orElse(Money.ZERO);
  }
}
