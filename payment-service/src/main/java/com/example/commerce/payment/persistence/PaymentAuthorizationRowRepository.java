package com.example.commerce.payment.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository over {@link PaymentRow}; used by {@link PostgresPaymentAuthorizationRepository}. */
public interface PaymentAuthorizationRowRepository extends CrudRepository<PaymentRow, String> {

  Optional<PaymentRow> findByIdempotencyKey(String idempotencyKey);
}
