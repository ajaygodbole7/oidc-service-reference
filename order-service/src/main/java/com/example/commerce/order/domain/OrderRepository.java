package com.example.commerce.order.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  /**
   * Returns candidate orders for a subject's own order-history page in descending id order.
   * The owner column narrows the cursor window only; SpiceDB is still the read authority.
   */
  List<Order> findPageByOwnerSub(String ownerSub, @Nullable String afterId, int limit);

  Order save(Order order);
}
