package com.example.commerce.order.integration;

import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.example.commerce.order.service.PaymentClient;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Service-to-service {@link PaymentClient}: obtains a client-credentials token for the
 * order-service Keycloak client (whose default scopes mint {@code aud=payment-service} and
 * {@code scope=payments:authorize}) and calls payment-service
 * {@code /internal/payments/authorize} with it.
 *
 * <p>Fails closed: any token-acquisition or authorization failure throws a
 * {@link PaymentClientException}, which aborts checkout before an order is persisted.
 *
 * <p>Failures are classified so the retry layer is safe:
 * <ul>
 *   <li>{@link TransientPaymentException} — connect/read timeout, I/O error, or a 5xx. The
 *       Idempotency-Key is sent on every attempt, so a retry is safe.
 *   <li>{@link PaymentClientException} (plain) — a 4xx, a declined / non-AUTHORIZED body, or a
 *       missing payment id. A settled answer; it is NEVER retried.
 * </ul>
 *
 * <p>The token is fetched per call rather than cached, mirroring the reference's choice to keep
 * service credentials instantly revocable instead of holding a bearer in memory. Transport timeouts
 * are bound on the injected {@link RestClient} (see {@code OrderConfig}); without them a hung
 * Keycloak/payment-service would pin the checkout thread.
 */
public final class HttpPaymentClient implements PaymentClient {

  private final RestClient http;
  private final String authorizeUri;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;

  public HttpPaymentClient(
      RestClient http,
      String authorizeUri,
      String tokenUri,
      String clientId,
      String clientSecret) {
    this.http = http;
    this.authorizeUri = authorizeUri;
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  @Override
  public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
    String token = fetchServiceToken();
    PaymentAuthorizeResponse response;
    try {
      response = http.post()
          .uri(authorizeUri)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
          .header("Idempotency-Key", command.idempotencyKey().value())
          .contentType(MediaType.APPLICATION_JSON)
          .body(new PaymentAuthorizeRequest(
              command.orderId().value(),
              command.userSub(),
              command.amount().amount(),
              command.amount().currency()))
          .retrieve()
          .body(PaymentAuthorizeResponse.class);
    } catch (HttpServerErrorException serverError) {
      // 5xx: payment-service may recover; the Idempotency-Key makes a retry safe.
      throw new TransientPaymentException("payment authorization returned a server error", serverError);
    } catch (RuntimeException exception) {
      if (isTransient(exception)) {
        // Connect/read timeout or I/O error: no settled answer, safe to retry with the same key.
        throw new TransientPaymentException("payment authorization call timed out or failed in transit", exception);
      }
      // 4xx and anything else: a settled answer. Never retried.
      throw new PaymentClientException("payment authorization call failed", exception);
    }
    if (response == null || !"AUTHORIZED".equals(response.status())) {
      throw new PaymentClientException("payment authorization was not authorized");
    }
    if (response.paymentId() == null || response.paymentId().isBlank()) {
      throw new PaymentClientException("payment authorization returned no payment id");
    }
    return new PaymentAuthorization(response.paymentId());
  }

  private String fetchServiceToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    Map<?, ?> body;
    try {
      body = http.post()
          .uri(tokenUri)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .body(Map.class);
    } catch (HttpServerErrorException serverError) {
      throw new TransientPaymentException("client-credentials token request returned a server error", serverError);
    } catch (RuntimeException exception) {
      if (isTransient(exception)) {
        throw new TransientPaymentException(
            "client-credentials token request timed out or failed in transit", exception);
      }
      throw new PaymentClientException("client-credentials token request failed", exception);
    }
    Object accessToken = body == null ? null : body.get("access_token");
    if (!(accessToken instanceof String token) || token.isBlank()) {
      throw new PaymentClientException("client-credentials token response had no access_token");
    }
    return token;
  }

  /**
   * Transient = a connect/read timeout or an I/O error somewhere in the cause chain. The exact
   * wrapper varies by phase: a read timeout on the request surfaces as {@code ResourceAccessException}
   * but the same timeout during response-body extraction surfaces as a plain {@code RestClientException}
   * — both carry a {@link SocketTimeoutException} cause. Classifying by the cause chain (not the
   * wrapper type) keeps the retry decision correct regardless of which phase failed. 5xx is handled
   * separately as {@link HttpServerErrorException}; a 4xx is an {@code HttpClientErrorException} with
   * no timeout cause, so it falls through to permanent.
   */
  private static boolean isTransient(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException
          || cause instanceof InterruptedIOException
          || cause instanceof ConnectException
          || cause instanceof org.springframework.web.client.ResourceAccessException) {
        return true;
      }
    }
    return false;
  }

  record PaymentAuthorizeRequest(String orderId, String userSub, BigDecimal amount, String currency) {
  }

  record PaymentAuthorizeResponse(String paymentId, String orderId, String status) {
  }
}
