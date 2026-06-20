package com.example.commerce.cart.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link CartRow}; used by {@link PostgresCartRepository}. */
public interface CartRowRepository extends CrudRepository<CartRow, String> {

  Optional<CartRow> findByOwnerSub(String ownerSub);
}
