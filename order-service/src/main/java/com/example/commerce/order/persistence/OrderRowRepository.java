package com.example.commerce.order.persistence;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link OrderRow}; used by {@link PostgresOrderRepository}. */
public interface OrderRowRepository extends CrudRepository<OrderRow, String> {

  @Query("""
      SELECT *
      FROM orders
      WHERE id IN (:ids)
        AND (:afterId IS NULL OR id < :afterId)
      ORDER BY id DESC
      LIMIT :limit
      """)
  List<OrderRow> findPageByIdsDesc(
      @Param("ids") Collection<String> ids,
      @Param("afterId") @Nullable String afterId,
      @Param("limit") int limit);
}
