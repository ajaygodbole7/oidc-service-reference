package com.example.commerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.security.ServicePrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentApplicationServiceTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void authorizes_payment_for_order_service_caller() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);

    PaymentAuthorization authorization = service.authorize(
        orderService(),
        "pay-key-1",
        command("order-1", "alice", "19.99"));

    assertThat(authorization.status()).isEqualTo("AUTHORIZED");
    assertThat(authorization.orderId()).isEqualTo(new OrderId("order-1"));
    assertThat(authorization.userSub()).isEqualTo("alice");
    assertThat(authorization.authorizedAt()).isEqualTo(CLOCK.instant());
    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void idempotent_replay_with_same_key_and_body_returns_same_authorization() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);

    PaymentAuthorization first = service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));
    PaymentAuthorization second = service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));

    assertThat(second).isEqualTo(first);
    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void idempotency_collision_fails_before_new_authorization_is_saved() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);
    service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));

    assertThatThrownBy(() -> service.authorize(
            orderService(),
            "pay-key-1",
            command("order-1", "alice", "29.99")))
        .isInstanceOf(PaymentIdempotencyConflictException.class);

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void wrong_client_fails_before_authorization_is_saved() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);

    assertThatThrownBy(() -> service.authorize(
            new ServicePrincipal("catalog-service", Set.of("payments:authorize"), "fp"),
            "pay-key-1",
            command("order-1", "alice", "19.99")))
        .isInstanceOf(PaymentAuthorizationException.class)
        .hasMessageContaining("unexpected payment caller");

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void missing_payment_scope_fails_before_authorization_is_saved() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);

    assertThatThrownBy(() -> service.authorize(
            new ServicePrincipal("order-service", Set.of("service.jobs"), "fp"),
            "pay-key-1",
            command("order-1", "alice", "19.99")))
        .isInstanceOf(PaymentAuthorizationException.class)
        .hasMessageContaining("payments:authorize");

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void idempotency_key_is_required() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = new PaymentApplicationService(repository, CLOCK);

    assertThatThrownBy(() -> service.authorize(orderService(), " ", command("order-1", "alice", "19.99")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Idempotency-Key");

    assertThat(repository.saveCount()).isZero();
  }

  private static ServicePrincipal orderService() {
    return new ServicePrincipal("order-service", Set.of("payments:authorize"), "fp-order");
  }

  private static AuthorizePaymentCommand command(String orderId, String userSub, String amount) {
    return new AuthorizePaymentCommand(
        new OrderId(orderId),
        userSub,
        new Money(new BigDecimal(amount), "USD"));
  }

  private static final class RecordingRepository implements PaymentAuthorizationRepository {

    private final Map<String, PaymentAuthorization> byIdempotencyKey = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey) {
      return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public PaymentAuthorization save(PaymentAuthorization authorization) {
      saveCount++;
      byIdempotencyKey.put(authorization.idempotencyKey(), authorization);
      return authorization;
    }

    int saveCount() {
      return saveCount;
    }
  }
}
