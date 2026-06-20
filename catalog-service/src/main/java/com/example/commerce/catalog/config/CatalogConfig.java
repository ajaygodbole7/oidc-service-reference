package com.example.commerce.catalog.config;

import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.persistence.CatalogProductRowRepository;
import com.example.commerce.catalog.persistence.PostgresProductRepository;
import com.example.commerce.catalog.service.CatalogApplicationService;
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
class CatalogConfig {

  @Bean
  ProductRepository productRepository(
      CatalogProductRowRepository productRows, JdbcAggregateTemplate aggregateTemplate) {
    return new PostgresProductRepository(productRows, aggregateTemplate);
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
      @Value("${catalog.spicedb.target:spicedb:50051}") String target,
      @Value("${catalog.spicedb.preshared-key:LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY}")
          String presharedKey,
      @Value("${catalog.spicedb.plaintext:true}") boolean plaintext) {
    return new SpiceDbAuthorizationClient(target, presharedKey, plaintext);
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(
      @Value("${catalog.oidc.issuer-uri}") String issuer,
      @Value("${catalog.oidc.audience:commerce-api}") String audience,
      @Value("${catalog.oidc.jwks-uri}") URI jwksUri) {
    return CommerceJwtValidator.fromJwksUri(issuer, audience, jwksUri);
  }

  @Bean
  CatalogApplicationService catalogApplicationService(
      ProductRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    return new CatalogApplicationService(repository, scopeAuthorizer, resourceAuthorizer);
  }
}
