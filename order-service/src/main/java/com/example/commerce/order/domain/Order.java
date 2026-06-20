package com.example.commerce.order.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Order {

  private final OrderId id;
  private final String ownerSub;
  private final CartId sourceCartId;
  private final List<OrderLine> lines;
  private final Money total;
  private final String paymentAuthorizationId;
  private final Instant createdAt;
  private OrderStatus status;

  public Order(
      OrderId id,
      String ownerSub,
      CartId sourceCartId,
      List<OrderLine> lines,
      Money total,
      String paymentAuthorizationId,
      Instant createdAt,
      OrderStatus status) {
    this.id = Objects.requireNonNull(id, "id");
    if (ownerSub == null || ownerSub.isBlank()) {
      throw new IllegalArgumentException("ownerSub is required");
    }
    this.ownerSub = ownerSub;
    this.sourceCartId = Objects.requireNonNull(sourceCartId, "sourceCartId");
    if (lines == null || lines.isEmpty()) {
      throw new IllegalArgumentException("order must contain at least one line");
    }
    this.lines = List.copyOf(lines);
    this.total = Objects.requireNonNull(total, "total");
    if (paymentAuthorizationId == null || paymentAuthorizationId.isBlank()) {
      throw new IllegalArgumentException("paymentAuthorizationId is required");
    }
    this.paymentAuthorizationId = paymentAuthorizationId;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.status = Objects.requireNonNull(status, "status");
  }

  public static Order confirmed(
      OrderId id,
      String ownerSub,
      CartId sourceCartId,
      List<OrderLine> lines,
      Money total,
      String paymentAuthorizationId,
      Instant createdAt) {
    return new Order(
        id, ownerSub, sourceCartId, lines, total, paymentAuthorizationId, createdAt, OrderStatus.CONFIRMED);
  }

  public OrderId id() {
    return id;
  }

  public String ownerSub() {
    return ownerSub;
  }

  public CartId sourceCartId() {
    return sourceCartId;
  }

  public List<OrderLine> lines() {
    return Collections.unmodifiableList(lines);
  }

  public Money total() {
    return total;
  }

  public String paymentAuthorizationId() {
    return paymentAuthorizationId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public OrderStatus status() {
    return status;
  }

  public void cancel() {
    if (status == OrderStatus.CANCELLED) {
      throw new IllegalStateException("order already cancelled");
    }
    status = OrderStatus.CANCELLED;
  }

  public Order copy() {
    return new Order(id, ownerSub, sourceCartId, lines, total, paymentAuthorizationId, createdAt, status);
  }
}
