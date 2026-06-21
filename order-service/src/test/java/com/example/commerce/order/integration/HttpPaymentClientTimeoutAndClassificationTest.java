package com.example.commerce.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Proves the S2S transport is BOUNDED and that failures are classified for the scoped retry layer.
 * A real loopback server is used so the configured read timeout is actually exercised (a mock server
 * cannot fire a socket-read timeout).
 */
class HttpPaymentClientTimeoutAndClassificationTest {

  private static final Duration CONNECT = Duration.ofMillis(500);
  private static final Duration READ = Duration.ofMillis(300);

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void read_timeout_on_token_endpoint_is_transient() throws IOException {
    // Token endpoint sleeps past the read timeout: the bound client must give up, not hang forever.
    startServer(exchange -> {
      sleep(READ.toMillis() + 800);
      respond(exchange, 200, "{\"access_token\":\"s2s\"}");
    });
    HttpPaymentClient client = client();

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(TransientPaymentException.class);
  }

  @Test
  void server_error_5xx_is_transient() throws IOException {
    startServer(exchange -> {
      if (exchange.getRequestURI().getPath().endsWith("/token")) {
        respond(exchange, 200, "{\"access_token\":\"s2s\"}");
      } else {
        respond(exchange, 503, "{\"error\":\"unavailable\"}");
      }
    });
    HttpPaymentClient client = client();

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(TransientPaymentException.class);
  }

  @Test
  void client_error_4xx_is_permanent_not_transient() throws IOException {
    startServer(exchange -> {
      if (exchange.getRequestURI().getPath().endsWith("/token")) {
        respond(exchange, 200, "{\"access_token\":\"s2s\"}");
      } else {
        respond(exchange, 403, "{\"error\":\"forbidden\"}");
      }
    });
    HttpPaymentClient client = client();

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(PaymentClientException.class)
        .isNotInstanceOf(TransientPaymentException.class);
  }

  @Test
  void declined_authorization_body_is_permanent_not_transient() throws IOException {
    startServer(exchange -> {
      if (exchange.getRequestURI().getPath().endsWith("/token")) {
        respond(exchange, 200, "{\"access_token\":\"s2s\"}");
      } else {
        respond(exchange, 200, "{\"paymentId\":\"pay-1\",\"orderId\":\"order-1\",\"status\":\"DECLINED\"}");
      }
    });
    HttpPaymentClient client = client();

    assertThatThrownBy(() -> client.authorize(command()))
        .isInstanceOf(PaymentClientException.class)
        .isNotInstanceOf(TransientPaymentException.class);
  }

  @Test
  void payment_properties_carry_finite_default_timeouts() {
    com.example.commerce.order.config.OrderProperties.Payment payment =
        new com.example.commerce.order.config.OrderProperties.Payment();

    assertThat(payment.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(payment.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(payment.getMaxAttempts()).isEqualTo(3);
  }

  private HttpPaymentClient client() {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    RestClient http = OrderHttp.paymentClient(CONNECT, READ);
    return new HttpPaymentClient(http, base + "/authorize", base + "/token", "order-service", "s3cr3t");
  }

  private void startServer(Handler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      try {
        handler.handle(exchange);
      } catch (RuntimeException failure) {
        respond(exchange, 500, "{\"error\":\"test-handler\"}");
      }
    });
    server.start();
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
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

  @FunctionalInterface
  private interface Handler {
    void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
  }
}
