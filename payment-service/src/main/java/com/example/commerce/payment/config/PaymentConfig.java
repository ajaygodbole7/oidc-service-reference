package com.example.commerce.payment.config;

import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.payment.persistence.InMemoryPaymentAuthorizationRepository;
import com.example.commerce.payment.service.PaymentApplicationService;
import com.example.commerce.security.ServiceJwtValidator;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PaymentConfig {

  @Bean
  PaymentAuthorizationRepository paymentAuthorizationRepository() {
    return new InMemoryPaymentAuthorizationRepository();
  }

  @Bean
  Clock paymentClock() {
    return Clock.systemUTC();
  }

  @Bean
  PaymentApplicationService paymentApplicationService(
      PaymentAuthorizationRepository repository, Clock paymentClock) {
    return new PaymentApplicationService(repository, paymentClock);
  }

  @Bean
  ServiceJwtValidator serviceJwtValidator(
      @Value("${payment.oidc.issuer-uri}") String issuer,
      @Value("${payment.oidc.audience:payment-service}") String audience,
      @Value("${payment.oidc.expected-client-id:order-service}") String expectedClientId,
      @Value("${payment.oidc.required-scope:payments:authorize}") String requiredScope,
      @Value("${payment.oidc.jwks-uri}") URI jwksUri) {
    return ServiceJwtValidator.fromJwksUri(
        issuer, audience, expectedClientId, requiredScope, jwksUri);
  }
}
