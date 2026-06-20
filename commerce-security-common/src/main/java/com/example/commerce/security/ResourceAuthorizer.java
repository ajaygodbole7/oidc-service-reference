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
