package com.example.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.example.commerce.order.service.PaymentClient;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.Test;

/**
 * The retry layer must be SCOPED: a transient failure (timeout / 5xx) is retried with the same
 * Idempotency-Key, but a declined / 4xx / non-AUTHORIZED settled answer is never retried.
 */
class RetryingPaymentClientTest {

  private static final Duration NO_BACKOFF = Duration.ZERO;

  @Test
  void retries_transient_failures_then_succeeds_within_the_attempt_budget() {
    ScriptedPaymentClient delegate = new ScriptedPaymentClient()
        .thenThrow(new TransientPaymentException("timeout", new RuntimeException()))
        .thenThrow(new TransientPaymentException("5xx", new RuntimeException()))
        .thenReturn(new PaymentAuthorization("pay-1"));
    RetryingPaymentClient client = new RetryingPaymentClient(delegate, 3, NO_BACKOFF);

    PaymentAuthorization authorization = client.authorize(command());

    assertThat(authorization.authorizationId()).isEqualTo("pay-1");
    assertThat(delegate.attempts()).isEqualTo(3);
  }

  @Test
  void exhausts_attempts_on_persistent_transient_failure_and_rethrows_last() {
    ScriptedPaymentClient delegate = new ScriptedPaymentClient()
        .thenThrow(new TransientPaymentException("timeout-1", new RuntimeException()))
        .thenThrow(new TransientPaymentException("timeout-2", new RuntimeException()))
        .thenThrow(new TransientPaymentException("timeout-3", new RuntimeException()));
    RetryingPaymentClient client = new RetryingPaymentClient(delegate, 3, NO_BACKOFF);

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(TransientPaymentException.class)
        .hasMessageContaining("timeout-3");
    assertThat(delegate.attempts()).isEqualTo(3);
  }

  @Test
  void never_retries_a_declined_or_4xx_permanent_failure() {
    ScriptedPaymentClient delegate = new ScriptedPaymentClient()
        .thenThrow(new PaymentClientException("payment authorization was not authorized"));
    RetryingPaymentClient client = new RetryingPaymentClient(delegate, 3, NO_BACKOFF);

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(PaymentClientException.class)
        .isNotInstanceOf(TransientPaymentException.class);
    assertThat(delegate.attempts()).isEqualTo(1);
  }

  @Test
  void single_attempt_budget_does_not_retry_even_on_transient() {
    ScriptedPaymentClient delegate = new ScriptedPaymentClient()
        .thenThrow(new TransientPaymentException("timeout", new RuntimeException()));
    RetryingPaymentClient client = new RetryingPaymentClient(delegate, 1, NO_BACKOFF);

    assertThatThrownBy(() -> client.authorize(command())).isInstanceOf(TransientPaymentException.class);
    assertThat(delegate.attempts()).isEqualTo(1);
  }

  private static PaymentAuthorizationCommand command() {
    return new PaymentAuthorizationCommand(
        new OrderId("order-1"),
        "alice",
        new CartId("alice-cart"),
        Money.usd("12.50"),
        new IdempotencyKey("idem-1"),
        "pm-card-1");
  }

  /** Replays a scripted sequence of outcomes, counting how many times it was invoked. */
  private static final class ScriptedPaymentClient implements PaymentClient {

    private final Deque<Object> script = new ArrayDeque<>();
    private int attempts;

    ScriptedPaymentClient thenThrow(RuntimeException failure) {
      script.add(failure);
      return this;
    }

    ScriptedPaymentClient thenReturn(PaymentAuthorization authorization) {
      script.add(authorization);
      return this;
    }

    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
      attempts++;
      Object next = script.poll();
      if (next instanceof RuntimeException failure) {
        throw failure;
      }
      if (next instanceof PaymentAuthorization authorization) {
        return authorization;
      }
      throw new IllegalStateException("scripted client exhausted");
    }

    int attempts() {
      return attempts;
    }
  }
}
