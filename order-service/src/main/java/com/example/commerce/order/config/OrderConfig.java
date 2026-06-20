package com.example.commerce.order.config;

import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.persistence.InMemoryCartLookup;
import com.example.commerce.order.persistence.OrderIdempotencyRowRepository;
import com.example.commerce.order.persistence.OrderRowRepository;
import com.example.commerce.order.persistence.PostgresIdempotencyRepository;
import com.example.commerce.order.persistence.PostgresOrderRepository;
import com.example.commerce.order.service.CartLookup;
import com.example.commerce.order.service.IdempotencyRepository;
import com.example.commerce.order.service.OrderApplicationService;
import com.example.commerce.order.integration.HttpPaymentClient;
import com.example.commerce.order.service.OrderCheckoutPersistence;
import com.example.commerce.order.service.PaymentClient;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.CommerceJwtValidator;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SpiceDbAuthorizationClient;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.web.client.RestClient;

@Configuration
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
  PaymentClient paymentClient(
      @Value("${order.payment.authorize-uri}") String authorizeUri,
      @Value("${order.payment.token-uri}") String tokenUri,
      @Value("${order.payment.client-id:order-service}") String clientId,
      @Value("${order.payment.client-secret}") String clientSecret) {
    // RestClient.create() rather than an injected RestClient.Builder: order-service's context
    // does not auto-configure a RestClient.Builder bean, and a static client needs none.
    return new HttpPaymentClient(
        RestClient.create(), authorizeUri, tokenUri, clientId, clientSecret);
  }

  @Bean(destroyMethod = "close")
  SpiceDbAuthorizationClient authorizationClient(
      @Value("${order.spicedb.target:spicedb:50051}") String target,
      @Value("${order.spicedb.preshared-key:LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY}")
          String presharedKey,
      @Value("${order.spicedb.plaintext:true}") boolean plaintext) {
    return new SpiceDbAuthorizationClient(target, presharedKey, plaintext);
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(
      @Value("${order.oidc.issuer-uri}") String issuer,
      @Value("${order.oidc.audience:commerce-api}") String audience,
      @Value("${order.oidc.jwks-uri}") URI jwksUri) {
    return CommerceJwtValidator.fromJwksUri(issuer, audience, jwksUri);
  }

  @Bean
  OrderApplicationService orderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      OrderCheckoutPersistence orderCheckoutPersistence,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    return new OrderApplicationService(
        orderRepository,
        cartLookup,
        idempotencyRepository,
        orderCheckoutPersistence,
        paymentClient,
        scopeAuthorizer,
        resourceAuthorizer);
  }
}
