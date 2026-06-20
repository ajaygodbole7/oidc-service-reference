package com.example.commerce.catalog.persistence;

import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link ProductRow}; used by {@link PostgresProductRepository}. */
public interface CatalogProductRowRepository extends CrudRepository<ProductRow, String> {
}
