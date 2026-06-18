package com.example.commerce.security;

/** Gate 3 of the four-gate ladder: coarse action authorization by JWT scope. */
public final class ScopeAuthorizer {

  public DecisionTrace requireScope(CommercePrincipal principal, String scope) {
    if (scope == null || scope.isBlank()) {
      DecisionTrace trace = DecisionTrace.scope(
          false, "(blank)", principal.subject(), principal.tokenFingerprint());
      throw new AuthorizationDeniedException("scope is required", trace);
    }

    boolean allowed = principal.hasScope(scope);
    DecisionTrace trace = DecisionTrace.scope(
        allowed, scope, principal.subject(), principal.tokenFingerprint());
    if (!allowed) {
      throw new AuthorizationDeniedException("missing required scope " + scope, trace);
    }
    return trace;
  }
}
