package com.example.commerce.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  List<Order> findByOwnerSub(String ownerSub);

  Order save(Order order);
}
