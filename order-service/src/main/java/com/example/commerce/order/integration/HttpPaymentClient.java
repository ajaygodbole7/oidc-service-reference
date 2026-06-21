package com.example.commerce.order.integration;

import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.example.commerce.order.service.PaymentClient;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Service-to-service {@link PaymentClient}: obtains a client-credentials token for the
 * order-service Keycloak client (whose default scopes mint {@code aud=payment-service} and
 * {@code scope=payments:authorize}) and calls payment-service
 * {@code /internal/payments/authorize} with it.
 *
 * <p>Fails closed: any token-acquisition or authorization failure throws
 * {@link PaymentClientException}, which aborts checkout before an order is persisted.
 *
 * <p>The token is fetched per call rather than cached, mirroring the reference's choice to
 * keep service credentials instantly revocable instead of holding a bearer in memory.
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
    } catch (RuntimeException exception) {
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
    } catch (RuntimeException exception) {
      throw new PaymentClientException("client-credentials token request failed", exception);
    }
    Object accessToken = body == null ? null : body.get("access_token");
    if (!(accessToken instanceof String token) || token.isBlank()) {
      throw new PaymentClientException("client-credentials token response had no access_token");
    }
    return token;
  }

  record PaymentAuthorizeRequest(String orderId, String userSub, BigDecimal amount, String currency) {
  }

  record PaymentAuthorizeResponse(String paymentId, String orderId, String status) {
  }
}
