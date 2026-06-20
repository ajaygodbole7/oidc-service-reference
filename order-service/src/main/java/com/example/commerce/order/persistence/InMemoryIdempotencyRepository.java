package com.example.commerce.order.persistence;

import com.example.commerce.order.domain.IdempotencyKey;
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
  public void save(IdempotencyRecord record) {
    records.put(new Key(record.subject(), record.key().value()), record);
  }

  private record Key(String subject, String key) {
  }
}
