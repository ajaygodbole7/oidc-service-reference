package com.example.commerce.order.service;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import java.util.Optional;

public interface IdempotencyRepository {

  Optional<IdempotencyRecord> find(String subject, IdempotencyKey key);

  boolean claim(String subject, IdempotencyKey key, String requestFingerprint, OrderId reservedOrderId);

  void linkOrder(String subject, IdempotencyKey key, OrderId orderId);
}
