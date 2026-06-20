package com.example.commerce.order.service;

import com.example.commerce.order.domain.IdempotencyKey;
import java.util.Optional;

public interface IdempotencyRepository {

  Optional<IdempotencyRecord> find(String subject, IdempotencyKey key);

  void save(IdempotencyRecord record);
}
