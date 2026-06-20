package com.example.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpPaymentClientTest {

  private static final String TOKEN_URI = "http://keycloak/token";
  private static final String AUTHORIZE_URI = "http://payment/internal/payments/authorize";

  private MockRestServiceServer server;
  private HttpPaymentClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    client = new HttpPaymentClient(builder.build(), AUTHORIZE_URI, TOKEN_URI, "order-service", "s3cr3t");
  }

  @Test
  void obtainsServiceTokenThenAuthorizesPaymentWithBearer() {
    server.expect(requestTo(TOKEN_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(Matchers.containsString("grant_type=client_credentials")))
        .andExpect(content().string(Matchers.containsString("client_id=order-service")))
        .andRespond(withSuccess(
            "{\"access_token\":\"s2s-token\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(AUTHORIZE_URI))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer s2s-token"))
        .andExpect(header("Idempotency-Key", "idem-1"))
        .andExpect(jsonPath("$.orderId").value("order-1"))
        .andExpect(jsonPath("$.userSub").value("alice"))
        .andExpect(jsonPath("$.amount").value(12.50))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andRespond(withSuccess(
            "{\"paymentId\":\"pay-1\",\"orderId\":\"order-1\",\"status\":\"AUTHORIZED\","
                + "\"currency\":\"USD\",\"amountCents\":1250}",
            MediaType.APPLICATION_JSON));

    PaymentAuthorization authorization = client.authorize(command());

    assertThat(authorization.authorizationId()).isEqualTo("pay-1");
    server.verify();
  }

  @Test
  void failsClosedWhenPaymentDenies() {
    server.expect(requestTo(TOKEN_URI))
        .andRespond(withSuccess("{\"access_token\":\"s2s-token\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(AUTHORIZE_URI))
        .andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(PaymentClientException.class);
  }

  @Test
  void failsClosedWhenTokenResponseHasNoAccessToken() {
    server.expect(requestTo(TOKEN_URI))
        .andRespond(withSuccess("{\"error\":\"invalid_client\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(PaymentClientException.class);
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
}
