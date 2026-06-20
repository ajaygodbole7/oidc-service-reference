package com.example.commerce.payment.persistence;

import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryPaymentAuthorizationRepository implements PaymentAuthorizationRepository {

  private final Map<String, PaymentAuthorization> byIdempotencyKey = new LinkedHashMap<>();

  @Override
  public synchronized Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
  }

  @Override
  public synchronized PaymentAuthorization save(PaymentAuthorization authorization) {
    byIdempotencyKey.put(authorization.idempotencyKey(), authorization);
    return authorization;
  }
}
