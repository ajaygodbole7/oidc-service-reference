package com.example.commerce.order.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  List<Order> findPageByOwnerSub(String ownerSub, @Nullable String afterId, int limit);

  Order save(Order order);
}
