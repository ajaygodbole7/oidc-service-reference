package com.example.commerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.payment.domain.Money;
import com.example.commerce.payment.domain.OrderId;
import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.payment.domain.PaymentStatus;
import com.example.commerce.security.ServicePrincipal;
import com.example.commerce.web.tsid.TsidGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaymentApplicationServiceTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

  /** Deterministic stub: emits {@code id-1}, {@code id-2}, ... so id assertions are stable. */
  private static TsidGenerator sequentialTsid() {
    AtomicInteger counter = new AtomicInteger();
    return () -> "id-" + counter.incrementAndGet();
  }

  private static PaymentApplicationService newService(PaymentAuthorizationRepository repository) {
    return new PaymentApplicationService(repository, sequentialTsid(), CLOCK);
  }

  @Test
  void authorizes_payment_for_order_service_caller() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = newService(repository);

    PaymentAuthorization authorization = service.authorize(
        orderService(),
        "pay-key-1",
        command("order-1", "alice", "19.99"));

    assertThat(authorization.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(authorization.orderId()).isEqualTo(new OrderId("order-1"));
    assertThat(authorization.userSub()).isEqualTo("alice");
    assertThat(authorization.authorizedAt()).isEqualTo(CLOCK.instant());
    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void mints_payment_id_from_tsid_generator_without_legacy_pay_prefix() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = newService(repository);

    PaymentAuthorization authorization = service.authorize(
        orderService(),
        "pay-key-1",
        command("order-1", "alice", "19.99"));

    assertThat(authorization.paymentId()).doesNotStartWith("pay_");
    assertThat(authorization.paymentId()).isEqualTo("id-1");
  }

  @Test
  void idempotent_replay_with_same_key_and_body_returns_same_authorization() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = newService(repository);

    PaymentAuthorization first = service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));
    PaymentAuthorization second = service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));

    assertThat(second).isEqualTo(first);
    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void idempotency_collision_fails_before_new_authorization_is_saved() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = newService(repository);
    service.authorize(orderService(), "pay-key-1", command("order-1", "alice", "19.99"));

    assertThatThrownBy(() -> service.authorize(
            orderService(),
            "pay-key-1",
            command("order-1", "alice", "29.99")))
        .isInstanceOf(PaymentIdempotencyConflictException.class);

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void concurrent_create_on_same_key_replays_via_duplicate_key_recovery() {
    // #5 replay-on-concurrent-key: a racing create reaches save() because the first findByKey
    // missed; the unique idempotency_key fires DuplicateKeyException, the service re-finds and
    // replays the winner instead of surfacing the DB error.
    RecordingRepository repository = new RecordingRepository();
    repository.failNextSaveWithDuplicateKey();
    PaymentApplicationService service = newService(repository);

    PaymentAuthorization racedIn = service.authorize(
        orderService(), "pay-key-1", command("order-1", "alice", "19.99"));

    assertThat(racedIn.status()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(repository.saveAttempts()).isEqualTo(1);
    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void concurrent_create_on_same_key_with_different_body_rejects_after_replay_find() {
    RecordingRepository repository = new RecordingRepository();
    repository.seedConflictingWinner();
    PaymentApplicationService service = newService(repository);

    assertThatThrownBy(() -> service.authorize(
            orderService(), "pay-key-1", command("order-1", "alice", "29.99")))
        .isInstanceOf(PaymentIdempotencyConflictException.class);
  }

  @Test
  void wrong_client_fails_before_authorization_is_saved() {
    RecordingRepository repository = new RecordingRepository();
    PaymentApplicationService service = newService(repository);

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
    PaymentApplicationService service = newService(repository);

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
    PaymentApplicationService service = newService(repository);

    assertThatThrownBy(() -> service.authorize(orderService(), " ", command("order-1", "alice", "19.99")))
        .isInstanceOf(MissingIdempotencyKeyException.class)
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
    private int saveAttempts;
    private boolean failNextSaveWithDuplicateKey;
    private PaymentAuthorization winnerSeededOnDuplicate;

    @Override
    public Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey) {
      return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public PaymentAuthorization save(PaymentAuthorization authorization) {
      saveAttempts++;
      if (failNextSaveWithDuplicateKey) {
        failNextSaveWithDuplicateKey = false;
        // Simulate a concurrent insert winning the unique idempotency_key: seed the winner so the
        // post-DuplicateKeyException re-find returns it, then surface the constraint violation.
        PaymentAuthorization winner =
            winnerSeededOnDuplicate != null ? winnerSeededOnDuplicate : authorization;
        byIdempotencyKey.put(authorization.idempotencyKey(), winner);
        throw new org.springframework.dao.DuplicateKeyException("idempotency_key already present");
      }
      saveCount++;
      byIdempotencyKey.put(authorization.idempotencyKey(), authorization);
      return authorization;
    }

    /** The next save() throws DuplicateKeyException, seeding the racing caller's own row as winner. */
    void failNextSaveWithDuplicateKey() {
      this.failNextSaveWithDuplicateKey = true;
    }

    /**
     * The next save() throws DuplicateKeyException, but the re-find returns a winner with a
     * different command body (the 19.99 fingerprint), so the replay rejects the racing 29.99 retry.
     */
    void seedConflictingWinner() {
      this.failNextSaveWithDuplicateKey = true;
      this.winnerSeededOnDuplicate = new PaymentAuthorization(
          "id-winner",
          new OrderId("order-1"),
          "alice",
          new Money(new BigDecimal("19.99"), "USD"),
          PaymentStatus.AUTHORIZED,
          "pay-key-1",
          fingerprintFor("order-1", "alice", "19.99"),
          CLOCK.instant());
    }

    int saveCount() {
      return saveCount;
    }

    int saveAttempts() {
      return saveAttempts;
    }
  }

  private static String fingerprintFor(String orderId, String userSub, String amount) {
    String canonical = orderId + "\n" + userSub + "\n" + "USD" + "\n"
        + new BigDecimal(amount).setScale(2).toPlainString();
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
