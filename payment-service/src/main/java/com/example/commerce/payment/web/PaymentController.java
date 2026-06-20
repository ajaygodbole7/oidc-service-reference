package com.example.commerce.payment.web;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.service.AuthorizePaymentCommand;
import com.example.commerce.payment.service.PaymentApplicationService;
import com.example.commerce.security.ServicePrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {

  private final PaymentApplicationService service;

  PaymentController(PaymentApplicationService service) {
    this.service = service;
  }

  @PostMapping("/internal/payments/authorize")
  PaymentResponse authorize(
      @RequestAttribute("servicePrincipal") ServicePrincipal principal,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PaymentRequest.Authorize request) {
    AuthorizePaymentCommand command = new AuthorizePaymentCommand(
        new OrderId(request.orderId()),
        request.userSub(),
        new Money(request.amount(), request.currency()));
    return PaymentResponse.from(service.authorize(principal, idempotencyKey, command));
  }
}
