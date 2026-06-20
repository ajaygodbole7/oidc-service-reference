package com.example.commerce.order.web;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.CheckoutCommand;
import com.example.commerce.order.service.OrderApplicationService;
import com.example.commerce.security.CommercePrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController {

  private final OrderApplicationService service;

  OrderController(OrderApplicationService service) {
    this.service = service;
  }

  @PostMapping("/api/orders/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  OrderResponse checkout(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody OrderRequest.Checkout request) {
    CheckoutCommand command = new CheckoutCommand(request.paymentMethodId(), request.shippingPostalCode());
    return OrderResponse.from(service.checkout(principal, command, new IdempotencyKey(idempotencyKey)));
  }

  @GetMapping("/api/orders/{orderId}")
  OrderResponse order(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @PathVariable String orderId) {
    return OrderResponse.from(service.getOrder(principal, new OrderId(orderId)));
  }

  @PostMapping("/api/orders/{orderId}/cancel")
  OrderResponse cancel(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @PathVariable String orderId) {
    return OrderResponse.from(service.cancelOrder(principal, new OrderId(orderId)));
  }
}
