package com.example.commerce.order.domain;

import java.util.Optional;

public interface OrderRepository {

  Optional<Order> findById(OrderId orderId);

  Order save(Order order);
}
