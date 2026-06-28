package com.example.commerce.order.web;

import com.example.commerce.order.service.OrderResult;
import java.util.List;

record OrderListResponse(List<OrderResponse> orders) {

  static OrderListResponse from(List<OrderResult> results) {
    return new OrderListResponse(results.stream().map(OrderResponse::from).toList());
  }
}
