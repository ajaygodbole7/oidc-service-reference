package com.example.commerce.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed model for the {@code payment.oidc.*} cluster that configures the S2S
 * {@link com.example.commerce.security.ServiceJwtValidator}, replacing the {@code @Value} cluster in
 * {@link PaymentConfig}. {@code expectedClientId=order-service} is the sole allowed caller and is not
 * a secret, so it keeps a default; the issuer and JWKS URIs are required and validated.
 */
@Validated
@ConfigurationProperties(prefix = "payment.oidc")
public record PaymentProperties(
    @NotBlank String issuerUri,
    @NotBlank @DefaultValue("payment-service") String audience,
    @NotBlank @DefaultValue("order-service") String expectedClientId,
    @NotBlank @DefaultValue("payments:authorize") String requiredScope,
    @NotNull URI jwksUri) {
}
