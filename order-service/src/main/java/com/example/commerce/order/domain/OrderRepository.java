package com.example.commerce.order.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  /** Returns all orders in descending id order, without an ownership filter.
   *  SpiceDB is the authority for who may see each order — do not filter by owner in SQL. */
  List<Order> findPage(@Nullable String afterId, int limit);

  Order save(Order order);
}
