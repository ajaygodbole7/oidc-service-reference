package com.example.commerce.cart.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed model for the {@code cart.*} cluster, replacing the {@code @Value} reads in
 * {@link CartConfig}. Bean-validation runs at boot, so a missing required value fails fast.
 *
 * <p>The SpiceDB preshared key has NO default on purpose: an unset
 * {@code cart.spicedb.preshared-key} (env {@code CART_SPICEDB_PRESHARED_KEY}) fails
 * {@link NotBlank} at boot rather than silently shipping a dev key — fail-closed. Non-secret
 * values keep dev-friendly defaults via {@link DefaultValue}.
 */
@Validated
@ConfigurationProperties(prefix = "cart")
public record CartProperties(@Valid @NotNull Oidc oidc, @Valid @NotNull Spicedb spicedb) {

  /** OIDC validation inputs for {@code CommerceJwtValidator}. */
  public record Oidc(
      @NotNull URI issuerUri,
      @NotBlank @DefaultValue("commerce-api") String audience,
      @NotNull URI jwksUri) {
  }

  /** SpiceDB connection inputs. {@code presharedKey} has no default: fail-closed if unset. */
  public record Spicedb(
      @NotBlank @DefaultValue("spicedb:50051") String target,
      @NotBlank String presharedKey,
      @DefaultValue("true") boolean plaintext) {
  }
}
