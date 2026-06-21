package com.example.commerce.cart.config;

import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.persistence.CartRowRepository;
import com.example.commerce.cart.persistence.PostgresCartRepository;
import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.CommerceJwtValidator;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SpiceDbAuthorizationClient;
import com.example.commerce.web.tsid.TsidGenerator;
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
  SpiceDbAuthorizationClient authorizationClient(CartProperties properties) {
    CartProperties.Spicedb spicedb = properties.spicedb();
    return new SpiceDbAuthorizationClient(spicedb.target(), spicedb.presharedKey(), spicedb.plaintext());
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(CartProperties properties) {
    CartProperties.Oidc oidc = properties.oidc();
    return CommerceJwtValidator.fromJwksUri(oidc.issuerUri().toString(), oidc.audience(), oidc.jwksUri());
  }

  @Bean
  CartApplicationService cartApplicationService(
      CartRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      TsidGenerator tsid) {
    return new CartApplicationService(
        repository, scopeAuthorizer, resourceAuthorizer, () -> new CartId(tsid.newId()));
  }
}
