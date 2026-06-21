package com.example.commerce.catalog.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public interface ProductRepository {

  List<Product> findAll();

  /**
   * One keyset page ordered by id: products with {@code id > afterId} (or from the start when
   * {@code afterId} is null), capped at {@code limit} rows. Callers pass {@code pageSize + 1} as
   * {@code limit} to over-fetch and detect a next page.
   */
  List<Product> findPage(@Nullable String afterId, int limit);

  Optional<Product> findById(ProductId productId);

  Product save(Product product);
}
