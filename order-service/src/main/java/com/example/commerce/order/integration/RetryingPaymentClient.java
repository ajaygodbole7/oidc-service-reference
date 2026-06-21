package com.example.commerce.order.integration;

import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.example.commerce.order.service.PaymentClient;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Narrow, transient-only retry layer over {@link HttpPaymentClient}. Retries ONLY
 * {@link TransientPaymentException} (connect/read timeout, I/O error, 5xx) — never a plain
 * {@link PaymentClientException}, which is a declined / 4xx / non-AUTHORIZED settled answer. The
 * Idempotency-Key is sent on every attempt, so a retry can only ever collapse into the first result
 * downstream, never double-charge.
 *
 * <p>This is a SEPARATE bean from {@link HttpPaymentClient} on purpose. Spring Retry's
 * {@code @Retryable} (the obvious alternative) is woven by an AOP proxy, and a self-invocation — a
 * bean calling its own {@code @Retryable} method — bypasses that proxy, so the advice silently never
 * fires. An explicit loop on a distinct delegating bean sidesteps that trap entirely: the retry is
 * plain Java, it reads the typed {@link com.example.commerce.order.config.OrderProperties.Payment}
 * budget directly (no SpEL/Duration mismatch), and there is no proxy boundary to get wrong.
 *
 * <p>Backoff is exponential with full jitter (base, 2x base, ...), capped per attempt at 2x base, so
 * a herd of retries does not synchronize against a recovering payment-service.
 */
public final class RetryingPaymentClient implements PaymentClient {

  private final PaymentClient delegate;
  private final int maxAttempts;
  private final Duration baseBackoff;

  public RetryingPaymentClient(PaymentClient delegate, int maxAttempts, Duration baseBackoff) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    this.delegate = delegate;
    this.maxAttempts = maxAttempts;
    this.baseBackoff = baseBackoff;
  }

  @Override
  public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
    TransientPaymentException lastTransient = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return delegate.authorize(command);
      } catch (TransientPaymentException transientFailure) {
        lastTransient = transientFailure;
        if (attempt < maxAttempts) {
          sleepBackoff(attempt);
        }
      }
      // A plain PaymentClientException (declined / 4xx / non-AUTHORIZED) is intentionally NOT caught:
      // it is a settled answer and propagates immediately without a retry.
    }
    throw lastTransient;
  }

  private void sleepBackoff(int attempt) {
    long baseMillis = baseBackoff.toMillis();
    if (baseMillis <= 0) {
      return;
    }
    // Exponential ceiling for this attempt: base * 2^(attempt-1), then full jitter in [0, ceiling].
    long ceiling = baseMillis << (attempt - 1);
    long jittered = ThreadLocalRandom.current().nextLong(ceiling + 1);
    try {
      Thread.sleep(jittered);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new PaymentClientException("payment retry backoff interrupted", interrupted);
    }
  }
}
