package com.example.commerce.cart.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression guard for the SpiceDB preshared-key fail-closed contract.
 *
 * <p>{@code application.yml} binds the key via {@code ${CART_SPICEDB_PRESHARED_KEY:}} (empty default),
 * so an unset env var resolves to {@code ""} rather than the literal placeholder. This proves a blank
 * key fails {@code @NotBlank} at context load (fail-closed) and a present key binds — catching any
 * revert to a bare {@code ${...}} placeholder, which {@code @ConfigurationProperties} would leave as a
 * non-blank literal that passes {@code @NotBlank} (fail-open). No Spring context boot or Testcontainers
 * needed: {@link ApplicationContextRunner} exercises the real binding + validation in-memory.
 */
class CartPropertiesFailClosedTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(EnableCartProperties.class)
      .withPropertyValues(
          "cart.oidc.issuer-uri=https://issuer.example.test/realms/test",
          "cart.oidc.jwks-uri=https://issuer.example.test/realms/test/protocol/openid-connect/certs");

  @Test
  void blankPresharedKey_failsContextLoad() {
    runner.withPropertyValues("cart.spicedb.preshared-key=")
        .run(context -> assertThat(context).hasFailed()
            .getFailure()
            .hasStackTraceContaining("presharedKey")
            .hasStackTraceContaining("must not be blank"));
  }

  @Test
  void presentPresharedKey_loadsContext() {
    runner.withPropertyValues("cart.spicedb.preshared-key=TEST_SPICEDB_PRESHARED_KEY")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(CartProperties.class).spicedb().presharedKey())
              .isEqualTo("TEST_SPICEDB_PRESHARED_KEY");
        });
  }

  @EnableConfigurationProperties(CartProperties.class)
  static class EnableCartProperties {
  }
}
