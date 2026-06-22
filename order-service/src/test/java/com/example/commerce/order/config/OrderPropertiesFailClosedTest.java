package com.example.commerce.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression guard for the SpiceDB preshared-key fail-closed contract.
 *
 * <p>{@code application.yml} binds the key via {@code ${SPICEDB_PRESHARED_KEY:}} (empty default), so an
 * unset env var resolves to {@code ""} rather than the literal placeholder. This proves a blank key
 * fails {@code @NotBlank} at context load (fail-closed) and a present key binds — catching any revert to
 * a bare {@code ${...}} placeholder, which {@code @ConfigurationProperties} would leave as a non-blank
 * literal that passes {@code @NotBlank} (fail-open). No Spring context boot or Testcontainers needed:
 * {@link ApplicationContextRunner} exercises the real binding + validation in-memory.
 *
 * <p>order-service's other required fields (issuer URI, payment URIs + client secret) are supplied so
 * the only outstanding violation is the preshared key.
 */
class OrderPropertiesFailClosedTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(EnableOrderProperties.class)
      .withPropertyValues(
          "order.oidc.issuer-uri=https://issuer.example.test/realms/test",
          "order.payment.authorize-uri=http://payment-service:8085/internal/payments/authorize",
          "order.payment.token-uri=https://issuer.example.test/realms/test/protocol/openid-connect/token",
          "order.payment.client-secret=test-client-secret");

  @Test
  void blankPresharedKey_failsContextLoad() {
    runner.withPropertyValues("order.spicedb.preshared-key=")
        .run(context -> assertThat(context).hasFailed()
            .getFailure()
            .hasStackTraceContaining("presharedKey")
            .hasStackTraceContaining("must not be blank"));
  }

  @Test
  void presentPresharedKey_loadsContext() {
    runner.withPropertyValues("order.spicedb.preshared-key=TEST_SPICEDB_PRESHARED_KEY")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(OrderProperties.class).getSpicedb().getPresharedKey())
              .isEqualTo("TEST_SPICEDB_PRESHARED_KEY");
        });
  }

  @EnableConfigurationProperties(OrderProperties.class)
  static class EnableOrderProperties {
  }
}
