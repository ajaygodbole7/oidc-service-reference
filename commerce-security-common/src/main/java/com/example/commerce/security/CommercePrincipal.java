package com.example.commerce.security;

import java.util.Set;

/**
 * The authenticated caller derived from a validated service JWT.
 *
 * <p>Carries only what the gates need: the subject, the coarse scopes (gate 3), and a
 * non-reversible token fingerprint for trace correlation. It never carries the raw token.
 */
public record CommercePrincipal(String subject, Set<String> scopes, String tokenFingerprint) {

  public CommercePrincipal {
    scopes = Set.copyOf(scopes);
  }

  /** Coarse (gate-3) check: does the caller hold this scope? */
  public boolean hasScope(String scope) {
    return scopes.contains(scope);
  }
}
