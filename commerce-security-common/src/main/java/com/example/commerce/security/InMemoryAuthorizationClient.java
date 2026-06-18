package com.example.commerce.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test fake for the AuthorizationClient port. It grants explicit resource permissions and
 * does not attempt to evaluate a SpiceDB schema.
 */
public final class InMemoryAuthorizationClient implements AuthorizationClient {

  private final Set<Grant> grants = ConcurrentHashMap.newKeySet();

  public InMemoryAuthorizationClient grant(
      SubjectRef subject, ResourceRef resource, Permission permission) {
    grants.add(new Grant(subject, resource, permission));
    return this;
  }

  public InMemoryAuthorizationClient grant(Relationship relationship) {
    return grant(relationship.subject(), relationship.resource(), new Permission(relationship.relation()));
  }

  @Override
  public AuthorizationDecision check(
      SubjectRef subject, ResourceRef resource, Permission permission) {
    boolean allowed = grants.contains(new Grant(subject, resource, permission));
    DecisionTrace trace = DecisionTrace.resource(
        allowed,
        subject,
        resource,
        permission,
        allowed ? "relationship_found" : "relationship_missing");
    return allowed ? AuthorizationDecision.allow(trace) : AuthorizationDecision.deny(trace);
  }

  private record Grant(SubjectRef subject, ResourceRef resource, Permission permission) {}
}
