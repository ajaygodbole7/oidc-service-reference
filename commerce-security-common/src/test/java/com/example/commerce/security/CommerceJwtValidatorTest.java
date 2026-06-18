package com.example.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Adversarial gate-2 validation against real crypto (no mocked validators): a correct token
 * is accepted, and every tampered shape — bad signature, wrong issuer/audience, expired,
 * unexpected {@code typ}, multi-aud without azp — is rejected. These are the checks a
 * dependency bump silently relaxed in {@code oidc-reference}; the suite is their teeth.
 */
class CommerceJwtValidatorTest {

  private static final String ISSUER = "https://idp.example/realms/commerce";
  private static final String AUDIENCE = "commerce-api";

  private static RSAKey signingKey;
  private static RSAKey attackerKey;
  private static CommerceJwtValidator validator;

  @BeforeAll
  static void setUp() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    // Same kid, different key — exercises the bad-signature / kid-swap path.
    attackerKey = new RSAKeyGenerator(2048).keyID("k1").generate();
    JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
    validator = new CommerceJwtValidator(ISSUER, AUDIENCE, jwks);
  }

  @Test
  void accepts_valid_token_and_extracts_subject_and_scopes() throws Exception {
    CommercePrincipal principal = validator.validate(sign(signingKey, JOSEObjectType.JWT, base().build()));
    assertThat(principal.subject()).isEqualTo("alice");
    assertThat(principal.scopes()).containsExactlyInAnyOrder("cart:read", "cart:write");
    assertThat(principal.tokenFingerprint()).hasSize(16);
    assertThat(principal.hasScope("cart:write")).isTrue();
  }

  @Test
  void accepts_rfc9068_at_jwt_type() throws Exception {
    String token = sign(signingKey, new JOSEObjectType("at+JWT"), base().build());
    assertThat(validator.validate(token).subject()).isEqualTo("alice");
  }

  @Test
  void accepts_single_audience_string_shape() throws Exception {
    // Keycloak emits aud as a JSON string for a single audience; it must still pass.
    String token = sign(signingKey, JOSEObjectType.JWT, base().audience(AUDIENCE).build());
    assertThat(validator.validate(token).subject()).isEqualTo("alice");
  }

  @Test
  void rejects_bad_signature() throws Exception {
    String token = sign(attackerKey, JOSEObjectType.JWT, base().build());
    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_wrong_issuer() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT, base().issuer("https://evil.example").build());
    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_wrong_audience() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT, base().audience("other-api").build());
    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_expired_token() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().expirationTime(new Date(System.currentTimeMillis() - 60_000)).build());
    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_unexpected_typ() throws Exception {
    String token = sign(signingKey, new JOSEObjectType("foo"), base().build());
    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_multi_audience_without_azp() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().audience(List.of(AUDIENCE, "other-api")).build());
    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("azp");
  }

  @Test
  void accepts_multi_audience_with_azp() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().audience(List.of(AUDIENCE, "other-api")).claim("azp", "commerce-bff").build());
    assertThat(validator.validate(token).subject()).isEqualTo("alice");
  }

  // --- helpers ---

  private static JWTClaimsSet.Builder base() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .subject("alice")
        .claim("scope", "cart:read cart:write")
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 300_000));
  }

  private static String sign(RSAKey key, JOSEObjectType typ, JWTClaimsSet claims) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(typ).keyID("k1").build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
