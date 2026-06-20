package com.example.commerce.payment.domain;

import java.util.Optional;

public interface PaymentAuthorizationRepository {

  Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey);

  PaymentAuthorization save(PaymentAuthorization authorization);
}
