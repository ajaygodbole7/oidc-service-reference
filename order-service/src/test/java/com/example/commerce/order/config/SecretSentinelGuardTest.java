package com.example.commerce.order.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

/**
 * Focused unit tests for order-service's boot-time sentinel guard. Avoids @SpringBootTest
 * because the guard's behavior is entirely a function of the resolved properties + the active
 * profiles; we want fast, single-purpose assertions. order-service is the densest case (three
 * sentinel-defaulted secrets), so it carries the per-guard test.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecretSentinelGuardTest {
  private static final String DATASOURCE_SENTINEL =
      "LOCAL_DEV_POSTGRES_PASSWORD__CHANGE_BEFORE_DEPLOY";
  private static final String CLIENT_SECRET_SENTINEL =
      "LOCAL_DEV_SERVICE_CLIENT_SECRET__CHANGE_BEFORE_DEPLOY";

  @Test
  void refusesToStart_whenSentinelInUseUnderNoExplicitProfile() {
    // No active profile is NOT a local opt-in. A copied artifact run without an explicit
    // local/dev/test profile must FAIL CLOSED rather than ship a dev sentinel with only a
    // WARN (the unsafe-by-omission posture, SECURITY.md §D-1).
    var env = new MockEnvironment(); // no active profile
    env.setProperty("spring.datasource.password", DATASOURCE_SENTINEL);
    assertThatThrownBy(() -> new SecretSentinelGuard(env).validateOnStartup())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Refusing to run with default dev secrets");
  }

  @Test
  void refusesToStart_underAnyNonLocalNamedProfile_notJustProd() {
    var env = new MockEnvironment();
    env.setActiveProfiles("staging");
    env.setProperty("spring.datasource.password", "real-password");
    env.setProperty("order.payment.client-secret", CLIENT_SECRET_SENTINEL);
    assertThatThrownBy(() -> new SecretSentinelGuard(env).validateOnStartup())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Refusing to run with default dev secrets");
  }

  @Test
  void warnsButPasses_underExplicitLocalProfile(CapturedOutput out) {
    // An explicit local-dev profile (local/dev/test) is allow-listed: the sentinel warns but
    // does not abort boot — this is the path the live local stack takes.
    var env = new MockEnvironment();
    env.setActiveProfiles("local");
    env.setProperty("spring.datasource.password", DATASOURCE_SENTINEL);
    env.setProperty("order.payment.client-secret", CLIENT_SECRET_SENTINEL);
    new SecretSentinelGuard(env).validateOnStartup();
    // reached here without throwing — local profile must not fail closed
    assertThat(out.getOut()).contains("ORDER_DATASOURCE_PASSWORD is the local-dev sentinel");
    assertThat(out.getOut()).contains("ORDER_PAYMENT_CLIENT_SECRET is the local-dev sentinel");
  }

  @Test
  void silentWhenNoSentinelInUse(CapturedOutput out) {
    var env = new MockEnvironment();
    env.setProperty("spring.datasource.password", "real-password");
    env.setProperty("order.spicedb.preshared-key", "real-key");
    env.setProperty("order.payment.client-secret", "real-secret");
    new SecretSentinelGuard(env).validateOnStartup();
    assertThat(out.getOut()).doesNotContain("local-dev sentinel");
  }
}
