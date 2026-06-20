package com.example.commerce.order.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link OrderIdempotencyRow}. */
public interface OrderIdempotencyRowRepository extends CrudRepository<OrderIdempotencyRow, Long> {

  Optional<OrderIdempotencyRow> findBySubjectAndIdempotencyKey(String subject, String idempotencyKey);
}
