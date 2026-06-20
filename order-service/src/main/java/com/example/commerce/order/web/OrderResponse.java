package com.example.commerce.order.web;

import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.service.OrderResult;
import java.time.Instant;
import java.util.List;

record OrderResponse(
    String id,
    String status,
    String sourceCartId,
    String currency,
    long totalCents,
    Instant createdAt,
    List<Line> lines) {

  static OrderResponse from(OrderResult result) {
    Order order = result.order();
    return new OrderResponse(
        order.id().value(),
        order.status().name(),
        order.sourceCartId().value(),
        order.total().currency(),
        cents(order.total()),
        order.createdAt(),
        order.lines().stream().map(Line::from).toList());
  }

  record Line(String productId, int quantity, long unitPriceCents, long lineTotalCents) {

    static Line from(OrderLine line) {
      return new Line(
          line.productId().value(),
          line.quantity(),
          cents(line.unitPrice()),
          cents(line.lineTotal()));
    }
  }

  private static long cents(Money money) {
    return money.amount().movePointRight(2).longValueExact();
  }
}
