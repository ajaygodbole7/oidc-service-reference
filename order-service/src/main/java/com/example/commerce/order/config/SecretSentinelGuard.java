package com.example.commerce.order.config;

import com.example.commerce.security.SecretSentinel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Boot-time fail-closed guard against shipping a committed dev secret to a non-local profile.
 * order-service seeds dev defaults for its datasource password, its SpiceDB preshared key, and
 * the inter-service client secret used to call payment-service; each default carries the marker
 * {@value SecretSentinel#MARKER} (shared via {@link SecretSentinel}).
 *
 * <p>The guard fails closed: any secret still holding the sentinel aborts boot under
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
    String spicedbPresharedKey = env.getProperty("order.spicedb.preshared-key");
    String paymentClientSecret = env.getProperty("order.payment.client-secret");

    boolean datasourceIsSentinel = SecretSentinel.containsSentinel(datasourcePassword);
    boolean spicedbIsSentinel = SecretSentinel.containsSentinel(spicedbPresharedKey);
    boolean paymentClientSecretIsSentinel = SecretSentinel.containsSentinel(paymentClientSecret);

    if (!datasourceIsSentinel && !spicedbIsSentinel && !paymentClientSecretIsSentinel) {
      return;
    }

    if (!SecretSentinel.isLocalProfile(env.getActiveProfiles())) {
      // Fail-closed: only an explicit local-dev profile may run with dev secrets. The
      // exception aborts bean initialization, so the web server never starts and
      // SpringApplication.run() exits non-zero.
      throw new IllegalStateException(
          "Refusing to run with default dev secrets outside a local profile. Set "
              + "ORDER_DATASOURCE_PASSWORD, SPICEDB_PRESHARED_KEY, and "
              + "ORDER_PAYMENT_CLIENT_SECRET explicitly.");
    }

    if (datasourceIsSentinel) {
      log.warn("ORDER_DATASOURCE_PASSWORD is the local-dev sentinel — replace before any non-local deploy.");
    }
    if (spicedbIsSentinel) {
      log.warn("SPICEDB_PRESHARED_KEY is the local-dev sentinel — replace before any non-local deploy.");
    }
    if (paymentClientSecretIsSentinel) {
      log.warn("ORDER_PAYMENT_CLIENT_SECRET is the local-dev sentinel — replace before any non-local deploy.");
    }
  }
}
