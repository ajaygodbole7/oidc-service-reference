package com.example.commerce.payment.config;

import com.example.commerce.security.SecretSentinel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Boot-time fail-closed guard against shipping a committed dev secret to a non-local profile.
 * payment-service seeds a dev default for its datasource password so the stack is zero-config
 * locally; the default carries the marker {@value SecretSentinel#MARKER} (shared via
 * {@link SecretSentinel}). (payment-service has no SpiceDB key — it authorizes the calling
 * service via its JWT, not via ReBAC — so the datasource password is its only sentinel secret.)
 *
 * <p>The guard fails closed: the secret still holding the sentinel aborts boot under
 * <em>any</em> profile that is not an explicit local-dev profile (local/dev/test) — including
 * when no profile is active at all, so a copied artifact that forgets to set a profile cannot
 * ship a dev sentinel with only a WARN. Only an explicit local profile downgrades to a WARN.
 * The check runs at bean initialization ({@link PostConstruct}) so it aborts before the
 * embedded web server begins accepting traffic — not after, the way an ApplicationReadyEvent
 * listener would. This mirrors auth-service's SecretSentinelValidator for the domain tier.
 */
@Component
class SecretSentinelGuard {
  private static final Logger log = LoggerFactory.getLogger(SecretSentinelGuard.class);

  private final Environment env;

  SecretSentinelGuard(Environment env) {
    this.env = env;
  }

  @PostConstruct
  void validateOnStartup() {
    String datasourcePassword = env.getProperty("spring.datasource.password");

    if (!SecretSentinel.containsSentinel(datasourcePassword)) {
      return;
    }

    if (!SecretSentinel.isLocalProfile(env.getActiveProfiles())) {
      // Fail-closed: only an explicit local-dev profile may run with dev secrets. The
      // exception aborts bean initialization, so the web server never starts and
      // SpringApplication.run() exits non-zero.
      throw new IllegalStateException(
          "Refusing to run with default dev secrets outside a local profile. "
              + "Set PAYMENT_DATASOURCE_PASSWORD explicitly.");
    }

    log.warn("PAYMENT_DATASOURCE_PASSWORD is the local-dev sentinel — replace before any non-local deploy.");
  }
}
