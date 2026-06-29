package com.example.commerce.order.web;

import com.example.commerce.order.service.OrderResult;
import com.example.commerce.web.pagination.Page;
import java.util.List;
import org.jspecify.annotations.Nullable;

record OrderListResponse(List<OrderResponse> orders, @Nullable String nextCursor) {

  static OrderListResponse from(Page<OrderResult> page) {
    return new OrderListResponse(
        page.items().stream().map(OrderResponse::from).toList(), page.nextCursor());
  }
}
