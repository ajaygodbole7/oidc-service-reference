package com.example.commerce.order.config;

import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.integration.HttpPaymentClient;
import com.example.commerce.order.integration.OrderHttp;
import com.example.commerce.order.integration.RetryingPaymentClient;
import com.example.commerce.order.persistence.OrderIdempotencyRowRepository;
import com.example.commerce.order.persistence.OrderRowRepository;
import com.example.commerce.order.persistence.PostgresIdempotencyRepository;
import com.example.commerce.order.persistence.PostgresOrderRepository;
import com.example.commerce.order.persistence.InMemoryCartLookup;
import com.example.commerce.order.service.CartLookup;
import com.example.commerce.order.service.IdempotencyRepository;
import com.example.commerce.order.service.OrderApplicationService;
import com.example.commerce.order.service.OrderCheckoutPersistence;
import com.example.commerce.order.service.PaymentClient;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.CommerceJwtValidator;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SpiceDbAuthorizationClient;
import com.example.commerce.web.pagination.CursorPaginator;
import com.example.commerce.web.tsid.TsidGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OrderProperties.class)
class OrderConfig {

  @Bean
  OrderRepository orderRepository(OrderRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    return new PostgresOrderRepository(rows, aggregateTemplate);
  }

  @Bean
  CartLookup cartLookup() {
    return InMemoryCartLookup.withLocalFixtures();
  }

  @Bean
  IdempotencyRepository idempotencyRepository(
      OrderIdempotencyRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    return new PostgresIdempotencyRepository(rows, aggregateTemplate);
  }

  @Bean
  OrderCheckoutPersistence orderCheckoutPersistence(
      OrderRepository orderRepository, IdempotencyRepository idempotencyRepository) {
    return new OrderCheckoutPersistence(orderRepository, idempotencyRepository);
  }

  @Bean
  ScopeAuthorizer scopeAuthorizer() {
    return new ScopeAuthorizer();
  }

  @Bean
  ResourceAuthorizer resourceAuthorizer(AuthorizationClient authorizationClient) {
    return new ResourceAuthorizer(authorizationClient);
  }

  @Bean
  PaymentClient paymentClient(OrderProperties properties) {
    OrderProperties.Payment payment = properties.getPayment();
    // Finite transport timeouts on the S2S client (a bare RestClient.create() is infinite); a
    // separate RetryingPaymentClient bean then retries TRANSIENT failures only — never a decline/4xx.
    RestClient http = OrderHttp.paymentClient(payment.getConnectTimeout(), payment.getReadTimeout());
    HttpPaymentClient delegate = new HttpPaymentClient(
        http,
        payment.getAuthorizeUri(),
        payment.getTokenUri(),
        payment.getClientId(),
        payment.getClientSecret());
    return new RetryingPaymentClient(delegate, payment.getMaxAttempts(), payment.getRetryBackoff());
  }

  @Bean(destroyMethod = "close")
  SpiceDbAuthorizationClient authorizationClient(OrderProperties properties) {
    OrderProperties.SpiceDb spicedb = properties.getSpicedb();
    return new SpiceDbAuthorizationClient(
        spicedb.getTarget(), spicedb.getPresharedKey(), spicedb.isPlaintext());
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(OrderProperties properties) {
    OrderProperties.Oidc oidc = properties.getOidc();
    return CommerceJwtValidator.fromJwksUri(oidc.getIssuerUri(), oidc.getAudience(), oidc.getJwksUri());
  }

  @Bean
  OrderApplicationService orderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      OrderCheckoutPersistence orderCheckoutPersistence,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      CursorPaginator cursorPaginator,
      TsidGenerator tsidGenerator) {
    // Reserve-then-claim: a fresh OrderId is minted up front (TSID, sortable) and the idempotency
    // claim races on it; the recover-forward state machine keeps the SAME reserved id on replay.
    return new OrderApplicationService(
        orderRepository,
        cartLookup,
        idempotencyRepository,
        orderCheckoutPersistence,
        paymentClient,
        scopeAuthorizer,
        resourceAuthorizer,
        cursorPaginator,
        tsidGenerator);
  }
}
