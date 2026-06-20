package com.example.commerce.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Focused client-credentials JWT validator for the payment S2S gate.
 *
 * <p>It intentionally mirrors {@link CommerceJwtValidator}'s small Nimbus-based style
 * instead of using Spring Resource Server auto-configuration.
 */
public final class ServiceJwtValidator {

  private static final JWSAlgorithm RS256 = JWSAlgorithm.RS256;
  private static final JOSEObjectType AT_JWT = new JOSEObjectType("at+JWT");

  private final DefaultJWTProcessor<SecurityContext> processor;
  private final String expectedClientId;
  private final String requiredScope;

  ServiceJwtValidator(
      String issuer,
      String audience,
      String expectedClientId,
      String requiredScope,
      JWKSource<SecurityContext> jwks) {
    if (expectedClientId == null || expectedClientId.isBlank()) {
      throw new IllegalArgumentException("expected client id is required");
    }
    if (requiredScope == null || requiredScope.isBlank()) {
      throw new IllegalArgumentException("required scope is required");
    }
    var p = new DefaultJWTProcessor<SecurityContext>();
    p.setJWSKeySelector(new JWSVerificationKeySelector<>(RS256, jwks));
    p.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(Set.of(JOSEObjectType.JWT, AT_JWT)));
    p.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
        Set.of(audience),
        new JWTClaimsSet.Builder().issuer(issuer).build(),
        Set.of("exp"),
        Set.of()));
    this.processor = p;
    this.expectedClientId = expectedClientId;
    this.requiredScope = requiredScope;
  }

  public static ServiceJwtValidator fromJwksUri(
      String issuer,
      String audience,
      String expectedClientId,
      String requiredScope,
      URI jwksUri) {
    try {
      JWKSource<SecurityContext> jwks = JWKSourceBuilder.create(jwksUri.toURL())
          .retrying(true)
          .rateLimited(60_000L)
          .cache(600_000L, 15_000L)
          .refreshAheadCache(300_000L, true)
          .outageTolerant(900_000L)
          .build();
      return new ServiceJwtValidator(issuer, audience, expectedClientId, requiredScope, jwks);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize service JWT validator from JWKS URI", e);
    }
  }

  public ServicePrincipal validate(String token) {
    JWTClaimsSet claims;
    try {
      claims = processor.process(token, null);
    } catch (Exception e) {
      throw new InvalidTokenException("service-to-service JWT failed validation", e);
    }

    String clientId = callerClientId(claims);
    if (!expectedClientId.equals(clientId)) {
      throw new InvalidTokenException("unexpected service caller");
    }

    Set<String> scopes = scopes(claims);
    if (!scopes.contains(requiredScope)) {
      throw new InvalidTokenException("missing required service scope " + requiredScope);
    }
    return new ServicePrincipal(clientId, scopes, fingerprint(token));
  }

  private static String callerClientId(JWTClaimsSet claims) {
    String clientId = firstPresent(
        stringClaim(claims, "azp"),
        stringClaim(claims, "client_id"),
        stringClaim(claims, "appid"));
    if (clientId == null || clientId.isBlank()) {
      throw new InvalidTokenException("service token is missing caller identity");
    }
    return clientId;
  }

  private static Set<String> scopes(JWTClaimsSet claims) {
    String scope = stringClaim(claims, "scope");
    if (scope == null || scope.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toUnmodifiableSet());
  }

  private static @Nullable String firstPresent(@Nullable String first, @Nullable String second,
      @Nullable String third) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    if (third != null && !third.isBlank()) {
      return third;
    }
    return null;
  }

  private static @Nullable String stringClaim(JWTClaimsSet claims, String name) {
    try {
      return claims.getStringClaim(name);
    } catch (ParseException e) {
      return null;
    }
  }

  private static String fingerprint(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
