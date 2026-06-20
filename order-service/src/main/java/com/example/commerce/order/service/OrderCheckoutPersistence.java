package com.example.commerce.order.service;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

public class OrderCheckoutPersistence {

  private final OrderRepository orderRepository;
  private final IdempotencyRepository idempotencyRepository;

  public OrderCheckoutPersistence(
      OrderRepository orderRepository, IdempotencyRepository idempotencyRepository) {
    this.orderRepository = orderRepository;
    this.idempotencyRepository = idempotencyRepository;
  }

  @Transactional
  public Order persistAndLink(String subject, IdempotencyKey idempotencyKey, Order order) {
    Order saved = orderRepository.save(order);
    idempotencyRepository.linkOrder(subject, idempotencyKey, saved.id());
    return saved;
  }
}
