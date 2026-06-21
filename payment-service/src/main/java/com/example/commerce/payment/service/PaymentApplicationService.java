package com.example.commerce.payment.service;

import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.security.ServicePrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

public final class PaymentApplicationService {

  private final PaymentAuthorizationRepository repository;
  private final Clock clock;

  public PaymentApplicationService(PaymentAuthorizationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public PaymentAuthorization authorize(
      ServicePrincipal principal, String idempotencyKey, AuthorizePaymentCommand command) {
    requireOrderService(principal);
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    String commandFingerprint = fingerprint(command);
    return repository.findByIdempotencyKey(normalizedKey)
        .map(existing -> replayOrReject(existing, commandFingerprint))
        .orElseGet(() -> createAuthorization(normalizedKey, commandFingerprint, command));
  }

  private static void requireOrderService(ServicePrincipal principal) {
    if (!"order-service".equals(principal.clientId())) {
      throw new PaymentAuthorizationException("unexpected payment caller");
    }
    if (!principal.hasScope("payments:authorize")) {
      throw new PaymentAuthorizationException("missing required service scope payments:authorize");
    }
  }

  private static String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key is required");
    }
    return idempotencyKey.trim();
  }

  private PaymentAuthorization replayOrReject(
      PaymentAuthorization existing, String commandFingerprint) {
    if (!existing.commandFingerprint().equals(commandFingerprint)) {
      throw new PaymentIdempotencyConflictException("Idempotency-Key reused with different command");
    }
    return existing;
  }

  private PaymentAuthorization createAuthorization(
      String idempotencyKey, String commandFingerprint, AuthorizePaymentCommand command) {
    PaymentAuthorization authorization = new PaymentAuthorization(
        "pay_" + UUID.randomUUID(),
        command.orderId(),
        command.userSub(),
        command.amount(),
        "AUTHORIZED",
        idempotencyKey,
        commandFingerprint,
        clock.instant());
    try {
      return repository.save(authorization);
    } catch (DuplicateKeyException exception) {
      return repository.findByIdempotencyKey(idempotencyKey)
          .map(existing -> replayOrReject(existing, commandFingerprint))
          .orElseThrow(() -> exception);
    }
  }

  private static String fingerprint(AuthorizePaymentCommand command) {
    String canonical = command.orderId().value() + "\n"
        + command.userSub() + "\n"
        + command.amount().currency() + "\n"
        + command.amount().amount().toPlainString();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
