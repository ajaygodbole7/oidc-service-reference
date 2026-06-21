package com.example.commerce.payment.config;

import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.payment.persistence.PaymentAuthorizationRowRepository;
import com.example.commerce.payment.persistence.PostgresPaymentAuthorizationRepository;
import com.example.commerce.payment.service.PaymentApplicationService;
import com.example.commerce.security.ServiceJwtValidator;
import com.example.commerce.web.tsid.TsidGenerator;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
class PaymentConfig {

  @Bean
  PaymentAuthorizationRepository paymentAuthorizationRepository(
      PaymentAuthorizationRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    return new PostgresPaymentAuthorizationRepository(rows, aggregateTemplate);
  }

  @Bean
  Clock paymentClock() {
    return Clock.systemUTC();
  }

  @Bean
  PaymentApplicationService paymentApplicationService(
      PaymentAuthorizationRepository repository, TsidGenerator tsidGenerator, Clock paymentClock) {
    return new PaymentApplicationService(repository, tsidGenerator, paymentClock);
  }

  @Bean
  ServiceJwtValidator serviceJwtValidator(PaymentProperties properties) {
    return ServiceJwtValidator.fromJwksUri(
        properties.issuerUri(),
        properties.audience(),
        properties.expectedClientId(),
        properties.requiredScope(),
        properties.jwksUri());
  }
}
