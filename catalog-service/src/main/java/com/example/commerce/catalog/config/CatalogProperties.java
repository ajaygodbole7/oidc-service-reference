package com.example.commerce.catalog.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed catalog configuration, replacing the scattered {@code @Value} lookups in {@link CatalogConfig}.
 * Bean-Validation-checked at startup so a missing issuer or JWKS URI fails fast with a clear message
 * instead of surfacing as a null deep in the JWT validator. Defaults live here in Java; the deployment
 * overrides only what differs.
 */
@ConfigurationProperties("catalog")
@Validated
public class CatalogProperties {

  @Valid
  private final Oidc oidc;

  @Valid
  private final SpiceDb spicedb;

  public CatalogProperties(Oidc oidc, SpiceDb spicedb) {
    this.oidc = oidc;
    this.spicedb = spicedb == null ? new SpiceDb(null, null, true) : spicedb;
  }

  public Oidc getOidc() {
    return oidc;
  }

  public SpiceDb getSpicedb() {
    return spicedb;
  }

  /** OIDC settings for validating incoming user access tokens (aud = commerce-api). */
  public record Oidc(
      @NotBlank String issuerUri,
      @NotBlank String audience,
      @NotNull URI jwksUri) {

    public Oidc {
      if (audience == null || audience.isBlank()) {
        audience = "commerce-api";
      }
    }
  }

  /**
   * SpiceDb client settings for the resource-level (store#manage) authorization check. The
   * {@code presharedKey} has NO default on purpose: an unset {@code catalog.spicedb.preshared-key}
   * (env {@code CATALOG_SPICEDB_PRESHARED_KEY}) fails {@link NotBlank} at boot rather than silently
   * shipping a dev key. Fail-closed, matching cart and order.
   */
  public record SpiceDb(
      @NotBlank String target,
      @NotBlank String presharedKey,
      boolean plaintext) {

    public SpiceDb {
      if (target == null || target.isBlank()) {
        target = "spicedb:50051";
      }
    }
  }
}
