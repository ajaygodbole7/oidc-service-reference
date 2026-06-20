package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.IdempotencyRecord;
import com.example.commerce.order.service.IdempotencyRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryIdempotencyRepository implements IdempotencyRepository {

  private final Map<Key, IdempotencyRecord> records = new LinkedHashMap<>();

  @Override
  public Optional<IdempotencyRecord> find(String subject, IdempotencyKey key) {
    return Optional.ofNullable(records.get(new Key(subject, key.value())));
  }

  @Override
  public boolean claim(String subject, IdempotencyKey key, String requestFingerprint) {
    Key mapKey = new Key(subject, key.value());
    if (records.containsKey(mapKey)) {
      return false;
    }
    records.put(mapKey, new IdempotencyRecord(subject, key, requestFingerprint, null));
    return true;
  }

  @Override
  public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
    Key mapKey = new Key(subject, key.value());
    IdempotencyRecord existing = records.get(mapKey);
    if (existing == null) {
      throw new IllegalStateException("idempotency record not claimed");
    }
    records.put(mapKey, new IdempotencyRecord(subject, key, existing.requestFingerprint(), orderId));
  }

  private record Key(String subject, String key) {
  }
}
