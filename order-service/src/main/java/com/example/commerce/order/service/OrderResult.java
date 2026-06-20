package com.example.commerce.order.service;

import com.example.commerce.order.domain.Order;
import com.example.commerce.security.DecisionTrace;
import java.util.List;

public record OrderResult(Order order, List<DecisionTrace> traces) {

  public OrderResult {
    traces = List.copyOf(traces);
  }
}
