package com.example.commerce.security;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

  @Override
  public List<String> lookupResources(
      SubjectRef subject, String resourceType, Permission permission, ReadConsistency consistency) {
    // The fake is strongly consistent by construction, so consistency is ignored. Return the ids of
    // resources of the given type on which this subject holds the permission.
    return grants.stream()
        .filter(g -> g.subject().equals(subject)
            && g.resource().type().equals(resourceType)
            && g.permission().equals(permission))
        .map(g -> g.resource().id())
        .distinct()
        .collect(Collectors.toUnmodifiableList());
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
      return Set.of(new Permission("read"), new Permission("cancel"), new Permission("owned"));
    }
    if ("order".equals(resourceType) && "support".equals(relation)) {
      return Set.of(new Permission("read"));
    }
    return Set.of(new Permission(relation));
  }
}
