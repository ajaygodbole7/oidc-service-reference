package com.example.commerce.catalog.persistence;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repository over {@link ProductRow}; used by {@link PostgresProductRepository}. */
public interface CatalogProductRowRepository extends CrudRepository<ProductRow, String> {

  /**
   * One keyset page ordered by id. {@code afterId} null returns the first page; otherwise returns
   * rows with {@code id > afterId}. Ids are TSIDs, so {@code ORDER BY id} is insertion-time order and
   * {@code id > :afterId} is a stable cursor. {@code limit} is {@code pageSize + 1} to detect a next page.
   */
  @Query("""
      SELECT * FROM products
      WHERE (:afterId IS NULL OR id > :afterId)
      ORDER BY id
      LIMIT :limit
      """)
  List<ProductRow> findPage(@Param("afterId") @Nullable String afterId, @Param("limit") int limit);
}
