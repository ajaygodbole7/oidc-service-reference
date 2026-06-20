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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServiceJwtValidatorTest {

  private static final String ISSUER = "https://idp.example/realms/commerce";
  private static final String AUDIENCE = "payment-service";
  private static final String EXPECTED_CLIENT = "order-service";
  private static final String REQUIRED_SCOPE = "payments:authorize";

  private static RSAKey signingKey;
  private static RSAKey attackerKey;
  private static ServiceJwtValidator validator;

  @BeforeAll
  static void setUp() throws Exception {
    signingKey = new RSAKeyGenerator(2048).keyID("s2s").generate();
    attackerKey = new RSAKeyGenerator(2048).keyID("s2s").generate();
    JWKSource<SecurityContext> jwks = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
    validator = new ServiceJwtValidator(ISSUER, AUDIENCE, EXPECTED_CLIENT, REQUIRED_SCOPE, jwks);
  }

  @Test
  void accepts_order_service_client_credentials_token() throws Exception {
    ServicePrincipal principal = validator.validate(sign(signingKey, JOSEObjectType.JWT,
        base().claim("azp", EXPECTED_CLIENT).build()));

    assertThat(principal.clientId()).isEqualTo(EXPECTED_CLIENT);
    assertThat(principal.hasScope(REQUIRED_SCOPE)).isTrue();
    assertThat(principal.tokenFingerprint()).hasSize(16);
  }

  @Test
  void accepts_rfc9068_at_jwt_type() throws Exception {
    String token = sign(signingKey, new JOSEObjectType("at+JWT"),
        base().claim("azp", EXPECTED_CLIENT).build());

    assertThat(validator.validate(token).clientId()).isEqualTo(EXPECTED_CLIENT);
  }

  @Test
  void accepts_client_id_and_appid_caller_identity_fallbacks() throws Exception {
    String clientIdToken = sign(signingKey, JOSEObjectType.JWT,
        base().claim("client_id", EXPECTED_CLIENT).build());
    String appIdToken = sign(signingKey, JOSEObjectType.JWT,
        base().claim("appid", EXPECTED_CLIENT).build());
    String blankAzpToken = sign(signingKey, JOSEObjectType.JWT,
        base().claim("azp", " ").claim("client_id", EXPECTED_CLIENT).build());

    assertThat(validator.validate(clientIdToken).clientId()).isEqualTo(EXPECTED_CLIENT);
    assertThat(validator.validate(appIdToken).clientId()).isEqualTo(EXPECTED_CLIENT);
    assertThat(validator.validate(blankAzpToken).clientId()).isEqualTo(EXPECTED_CLIENT);
  }

  @Test
  void rejects_user_commerce_api_token() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().audience("commerce-api").subject("alice").claim("azp", "commerce-bff").build());

    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_wrong_service_client() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().claim("azp", "catalog-service").build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("unexpected service caller");
  }

  @Test
  void rejects_missing_required_payment_scope() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT,
        base().claim("azp", EXPECTED_CLIENT).claim("scope", "service.jobs").build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining(REQUIRED_SCOPE);
  }

  @Test
  void rejects_missing_caller_identity() throws Exception {
    String token = sign(signingKey, JOSEObjectType.JWT, base().build());

    assertThatThrownBy(() -> validator.validate(token))
        .isInstanceOf(InvalidTokenException.class)
        .hasMessageContaining("caller identity");
  }

  @Test
  void rejects_bad_signature() throws Exception {
    String token = sign(attackerKey, JOSEObjectType.JWT,
        base().claim("azp", EXPECTED_CLIENT).build());

    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void rejects_unexpected_type() throws Exception {
    String token = sign(signingKey, new JOSEObjectType("foo"),
        base().claim("azp", EXPECTED_CLIENT).build());

    assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(InvalidTokenException.class);
  }

  private static JWTClaimsSet.Builder base() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .claim("scope", REQUIRED_SCOPE)
        .issueTime(new Date())
        .expirationTime(new Date(System.currentTimeMillis() + 300_000));
  }

  private static String sign(RSAKey key, JOSEObjectType typ, JWTClaimsSet claims) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(typ).keyID("s2s").build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
