package com.example.commerce.order.persistence;

import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link OrderRow}; used by {@link PostgresOrderRepository}. */
public interface OrderRowRepository extends CrudRepository<OrderRow, String> {
}
