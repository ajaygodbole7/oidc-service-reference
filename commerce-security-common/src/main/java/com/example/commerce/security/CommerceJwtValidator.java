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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Gate 2 of the four-gate ladder: validates the gateway-injected service JWT and maps it to
 * a {@link CommercePrincipal}.
 *
 * <p>Hardened the way {@code oidc-reference} learned the hard way, with the checks kept
 * explicit so a dependency bump cannot silently relax them:
 * <ul>
 *   <li>RS256 signature against the IdP JWKS;</li>
 *   <li>issuer match and {@code aud=commerce-api}, accepting Keycloak's single-audience
 *       string shape and the multi-audience array shape;</li>
 *   <li>expiry (via the claims verifier);</li>
 *   <li>{@code typ} restricted to {@code JWT} or {@code at+JWT} (RFC 9068);</li>
 *   <li>an explicit azp-on-multi-audience guard.</li>
 * </ul>
 *
 * <p>Uses focused Nimbus JOSE rather than Spring OAuth2 Resource Server auto-configuration,
 * so the four gates stay visible in service code.
 */
public final class CommerceJwtValidator {

  private static final JWSAlgorithm RS256 = JWSAlgorithm.RS256;
  private static final JOSEObjectType AT_JWT = new JOSEObjectType("at+JWT");

  private final DefaultJWTProcessor<SecurityContext> processor;

  /**
   * Build a validator over an explicit JWKS source. Package-private so tests can inject an
   * in-memory JWK set; production code uses {@link #fromJwksUri}.
   */
  CommerceJwtValidator(String issuer, String audience, JWKSource<SecurityContext> jwks) {
    var p = new DefaultJWTProcessor<SecurityContext>();
    p.setJWSKeySelector(new JWSVerificationKeySelector<>(RS256, jwks));
    p.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(Set.of(JOSEObjectType.JWT, AT_JWT)));
    p.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
        audience,
        new JWTClaimsSet.Builder().issuer(issuer).build(),
        Set.of("sub", "exp")));
    this.processor = p;
  }

  /**
   * Build a validator that fetches and caches the IdP JWKS. The cache settings mirror the
   * auth-service: TTL above the refresh-ahead trigger, outage-tolerant, rate-limited.
   */
  public static CommerceJwtValidator fromJwksUri(String issuer, String audience, URI jwksUri) {
    try {
      JWKSource<SecurityContext> jwks = JWKSourceBuilder.create(jwksUri.toURL())
          .retrying(true)
          .rateLimited(60_000L)
          .cache(600_000L, 15_000L)
          .refreshAheadCache(300_000L, true)
          .outageTolerant(900_000L)
          .build();
      return new CommerceJwtValidator(issuer, audience, jwks);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to initialize JWT validator from JWKS URI", e);
    }
  }

  /** Validate the token and map it to a principal, or throw. Fail closed. */
  public CommercePrincipal validate(String token) {
    JWTClaimsSet claims;
    try {
      claims = processor.process(token, null);
    } catch (Exception e) {
      throw new InvalidTokenException("service JWT failed validation", e);
    }
    enforceAuthorizedParty(claims);
    return new CommercePrincipal(claims.getSubject(), scopes(claims), fingerprint(token));
  }

  // Multi-audience tokens MUST carry azp — the defense against audience confusion.
  // Explicit, not delegated to a library default that has shifted between releases.
  private static void enforceAuthorizedParty(JWTClaimsSet claims) {
    List<String> audiences = claims.getAudience();
    if (audiences != null && audiences.size() > 1 && stringClaim(claims, "azp") == null) {
      throw new InvalidTokenException("multi-audience token is missing azp");
    }
  }

  private static Set<String> scopes(JWTClaimsSet claims) {
    String scope = stringClaim(claims, "scope");
    if (scope == null || scope.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(scope.trim().split("\\s+")).collect(Collectors.toUnmodifiableSet());
  }

  private static @Nullable String stringClaim(JWTClaimsSet claims, String name) {
    try {
      return claims.getStringClaim(name);
    } catch (ParseException e) {
      return null;
    }
  }

  // Non-reversible correlation handle for the Security Trace — never the raw token.
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
