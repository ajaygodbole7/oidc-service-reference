package com.example.commerce.cart.service;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.security.DecisionTrace;
import java.util.List;

public record CartResult(Cart cart, List<DecisionTrace> traces) {

  public CartResult {
    traces = List.copyOf(traces);
  }
}
