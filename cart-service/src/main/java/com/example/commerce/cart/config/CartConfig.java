package com.example.commerce.cart.config;

import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.persistence.CartRowRepository;
import com.example.commerce.cart.persistence.PostgresCartRepository;
import com.example.commerce.cart.service.CartApplicationService;
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

@Configuration
class CartConfig {

  @Bean
  CartRepository cartRepository(CartRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    return new PostgresCartRepository(rows, aggregateTemplate);
  }

  @Bean
  ScopeAuthorizer scopeAuthorizer() {
    return new ScopeAuthorizer();
  }

  @Bean
  ResourceAuthorizer resourceAuthorizer(AuthorizationClient authorizationClient) {
    return new ResourceAuthorizer(authorizationClient);
  }

  @Bean(destroyMethod = "close")
  SpiceDbAuthorizationClient authorizationClient(
      @Value("${cart.spicedb.target:spicedb:50051}") String target,
      @Value("${cart.spicedb.preshared-key:LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY}")
          String presharedKey,
      @Value("${cart.spicedb.plaintext:true}") boolean plaintext) {
    return new SpiceDbAuthorizationClient(target, presharedKey, plaintext);
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(
      @Value("${cart.oidc.issuer-uri}") String issuer,
      @Value("${cart.oidc.audience:commerce-api}") String audience,
      @Value("${cart.oidc.jwks-uri}") URI jwksUri) {
    return CommerceJwtValidator.fromJwksUri(issuer, audience, jwksUri);
  }

  @Bean
  CartApplicationService cartApplicationService(
      CartRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    return new CartApplicationService(repository, scopeAuthorizer, resourceAuthorizer);
  }
}
