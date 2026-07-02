package com.example.commerce.order.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  /**
   * Returns the orders whose ids are in {@code allowedIds}, in descending id order, keyset-paginated
   * after {@code afterId}. The allowed-id set comes from SpiceDB LookupResources (the authority), so
   * this is a pure by-id fetch: Postgres owns the recency ordering, SpiceDB owns who may see what.
   * An empty {@code allowedIds} yields an empty page without touching the database.
   */
  List<Order> findPageByIdsDesc(Collection<String> allowedIds, @Nullable String afterId, int limit);

  Order save(Order order);
}
