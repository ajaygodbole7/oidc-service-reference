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
    permissionsFor(relationship).forEach(permission ->
        grant(relationship.subject(), relationship.resource(), permission));
    return this;
  }

  @Override
  public void writeRelationship(Relationship relationship) {
    grant(relationship);
  }

  @Override
  public void deleteRelationship(Relationship relationship) {
    permissionsFor(relationship).forEach(permission ->
        grants.remove(new Grant(relationship.subject(), relationship.resource(), permission)));
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

  private static Set<Permission> permissionsFor(Relationship relationship) {
    String resourceType = relationship.resource().type();
    String relation = relationship.relation();
    if ("cart".equals(resourceType) && "owner".equals(relation)) {
      return Set.of(new Permission("read"), new Permission("write"));
    }
    if ("store".equals(resourceType) && "manager".equals(relation)) {
      return Set.of(new Permission("view"), new Permission("manage"));
    }
    if ("order".equals(resourceType) && "owner".equals(relation)) {
      return Set.of(new Permission("read"), new Permission("cancel"));
    }
    if ("order".equals(resourceType) && "support".equals(relation)) {
      return Set.of(new Permission("read"));
    }
    return Set.of(new Permission(relation));
  }
}
