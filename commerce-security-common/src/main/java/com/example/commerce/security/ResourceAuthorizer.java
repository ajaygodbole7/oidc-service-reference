package com.example.commerce.security;

import java.util.Optional;

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
    } catch (AuthorizationUnavailableException e) {
      DecisionTrace trace = DecisionTrace.resource(
          false, subject, resource, permission, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource authorization unavailable", trace, e);
    }
    if (!decision.allowed()) {
      throw new AuthorizationDeniedException("resource authorization denied", decision.trace());
    }
    return decision.trace();
  }

  /**
   * For list/collection endpoints: returns the trace when allowed, empty when denied
   * (relationship_missing), and still throws when SpiceDB is unavailable (fail-closed).
   * Single-resource endpoints should use requireAllowed.
   */
  public Optional<DecisionTrace> filterAllowed(
      CommercePrincipal principal, ResourceRef resource, Permission permission) {
    SubjectRef subject = SubjectRef.user(principal.subject());
    AuthorizationDecision decision;
    try {
      decision = client.check(subject, resource, permission);
    } catch (AuthorizationUnavailableException e) {
      DecisionTrace trace = DecisionTrace.resource(
          false, subject, resource, permission, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource authorization unavailable", trace, e);
    }
    return decision.allowed() ? Optional.of(decision.trace()) : Optional.empty();
  }

  public DecisionTrace writeRelationship(Relationship relationship) {
    try {
      client.writeRelationship(relationship);
      return DecisionTrace.relationship(true, relationship, "relationship_written");
    } catch (AuthorizationUnavailableException e) {
      DecisionTrace trace =
          DecisionTrace.relationship(false, relationship, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource relationship write unavailable", trace, e);
    }
  }

  public DecisionTrace deleteRelationship(Relationship relationship) {
    try {
      client.deleteRelationship(relationship);
      return DecisionTrace.relationship(true, relationship, "relationship_deleted");
    } catch (AuthorizationUnavailableException e) {
      DecisionTrace trace =
          DecisionTrace.relationship(false, relationship, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource relationship delete unavailable", trace, e);
    }
  }
}
