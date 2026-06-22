package com.example.commerce.order.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for order-service, replacing the scattered {@code @Value} lookups in
 * {@link OrderConfig}. Validation fails the context at boot if a required value is missing, so a
 * misconfigured deploy never starts serving instead of failing the first request.
 *
 * <p>Note: {@code spicedb.preshared-key} has no shippable default. The field defaults to "" and
 * {@code application.yml} binds it via {@code ${SPICEDB_PRESHARED_KEY:}} (empty default), so an unset
 * key resolves to "" and fails {@link NotBlank} at boot rather than authorizing against SpiceDB with
 * a known key. The empty {@code :} default is required: {@code @ConfigurationProperties} leaves an
 * unresolved {@code ${...}} as a non-blank literal that would pass {@link NotBlank} (fail-open).
 */
@ConfigurationProperties("order")
@Validated
public class OrderProperties {

  @Valid
  private final Oidc oidc = new Oidc();

  @Valid
  private final SpiceDb spicedb = new SpiceDb();

  @Valid
  private final Payment payment = new Payment();

  public Oidc getOidc() {
    return oidc;
  }

  public SpiceDb getSpicedb() {
    return spicedb;
  }

  public Payment getPayment() {
    return payment;
  }

  /** Token-validation settings for the edge JWT filter. */
  public static class Oidc {

    @NotBlank
    private String issuerUri = "";

    @NotBlank
    private String audience = "commerce-api";

    @NotNull
    private URI jwksUri = URI.create("about:blank");

    public String getIssuerUri() {
      return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
      this.issuerUri = issuerUri;
    }

    public String getAudience() {
      return audience;
    }

    public void setAudience(String audience) {
      this.audience = audience;
    }

    public URI getJwksUri() {
      return jwksUri;
    }

    public void setJwksUri(URI jwksUri) {
      this.jwksUri = jwksUri;
    }
  }

  /** Authorization (SpiceDB) gRPC connection settings. */
  public static class SpiceDb {

    @NotBlank
    private String target = "spicedb:50051";

    /**
     * Defaults to "" (blank), which fails {@link NotBlank}. With application.yml binding
     * {@code ${SPICEDB_PRESHARED_KEY:}} (empty default), an unset key stays "" and fails the context
     * at boot (fail-closed) instead of shipping a placeholder.
     */
    @NotBlank
    private String presharedKey = "";

    private boolean plaintext = true;

    public String getTarget() {
      return target;
    }

    public void setTarget(String target) {
      this.target = target;
    }

    public String getPresharedKey() {
      return presharedKey;
    }

    public void setPresharedKey(String presharedKey) {
      this.presharedKey = presharedKey;
    }

    public boolean isPlaintext() {
      return plaintext;
    }

    public void setPlaintext(boolean plaintext) {
      this.plaintext = plaintext;
    }
  }

  /** Service-to-service payment client settings: endpoints, transport timeouts, and retry budget. */
  public static class Payment {

    @NotBlank
    private String authorizeUri = "";

    @NotBlank
    private String tokenUri = "";

    @NotBlank
    private String clientId = "order-service";

    @NotBlank
    private String clientSecret = "";

    /** TCP connect timeout for token + authorize calls. A hung Keycloak/payment must not pin a thread. */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** Socket read timeout for token + authorize calls. */
    @NotNull
    private Duration readTimeout = Duration.ofSeconds(5);

    /** Total attempts (1 = no retry) for a TRANSIENT failure only; declines and 4xx are never retried. */
    @Min(1)
    private int maxAttempts = 3;

    /** Base backoff between transient retries; jittered up to 2x in the client. */
    @NotNull
    private Duration retryBackoff = Duration.ofMillis(200);

    public String getAuthorizeUri() {
      return authorizeUri;
    }

    public void setAuthorizeUri(String authorizeUri) {
      this.authorizeUri = authorizeUri;
    }

    public String getTokenUri() {
      return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public Duration getRetryBackoff() {
      return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
      this.retryBackoff = retryBackoff;
    }
  }
}
