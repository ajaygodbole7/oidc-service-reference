package com.example.commerce.security;

import java.util.Set;

/**
 * Authenticated service caller derived from a validated client-credentials JWT.
 *
 * <p>Payment S2S tokens prove the calling service, not direct user consent. User
 * identifiers in downstream commands remain command data.
 */
public record ServicePrincipal(String clientId, Set<String> scopes, String tokenFingerprint) {

  public ServicePrincipal {
    scopes = Set.copyOf(scopes);
  }

  public boolean hasScope(String scope) {
    return scopes.contains(scope);
  }
}
