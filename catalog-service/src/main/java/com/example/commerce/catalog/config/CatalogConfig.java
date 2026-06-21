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
import com.example.commerce.web.pagination.CursorPaginator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
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
  SpiceDbAuthorizationClient authorizationClient(CatalogProperties properties) {
    CatalogProperties.SpiceDb spicedb = properties.getSpicedb();
    return new SpiceDbAuthorizationClient(
        spicedb.target(), spicedb.presharedKey(), spicedb.plaintext());
  }

  @Bean
  CommerceJwtValidator commerceJwtValidator(CatalogProperties properties) {
    CatalogProperties.Oidc oidc = properties.getOidc();
    return CommerceJwtValidator.fromJwksUri(oidc.issuerUri(), oidc.audience(), oidc.jwksUri());
  }

  @Bean
  CatalogApplicationService catalogApplicationService(
      ProductRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      CursorPaginator cursorPaginator) {
    return new CatalogApplicationService(
        repository, scopeAuthorizer, resourceAuthorizer, cursorPaginator);
  }
}
