package com.example.commerce.security;

/**
 * Gate 4 of the four-gate ladder. Wraps the authorization port and fails closed on
 * denied, malformed, or unavailable checks.
 */
public final class ResourceAuthorizer {

  private final AuthorizationClient client;

  public ResourceAuthorizer(AuthorizationClient client) {
    this.client = client;
  }

  public DecisionTrace requireAllowed(
      CommercePrincipal principal, ResourceRef resource, Permission permission) {
    SubjectRef subject = SubjectRef.user(principal.subject());
    AuthorizationDecision decision;
    try {
      decision = client.check(subject, resource, permission);
    } catch (RuntimeException e) {
      DecisionTrace trace = DecisionTrace.resource(
          false, subject, resource, permission, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource authorization unavailable", trace);
    }
    if (!decision.allowed()) {
      throw new AuthorizationDeniedException("resource authorization denied", decision.trace());
    }
    return decision.trace();
  }
}
