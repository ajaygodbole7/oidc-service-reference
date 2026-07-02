package com.example.commerce.security;

import java.util.List;

/**
 * Port for fine-grained resource authorization. Implementations may be a test fake or a
 * SpiceDB adapter, but service code depends only on this interface.
 */
public interface AuthorizationClient {

  /** Point check at the freshest revision (fully consistent). */
  AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission);

  /**
   * Point check at the requested {@link ReadConsistency}. Defaults to the fully-consistent
   * {@link #check(SubjectRef, ResourceRef, Permission)} so existing fakes/impls keep their behavior.
   */
  default AuthorizationDecision check(
      SubjectRef subject, ResourceRef resource, Permission permission, ReadConsistency consistency) {
    return check(subject, resource, permission);
  }

  /**
   * List authorization: the ids of {@code resourceType} objects on which {@code subject} holds
   * {@code permission}, evaluated at the requested consistency. This is the LookupResources gate —
   * SpiceDB is asked which resources are visible, rather than filtering a database page per row.
   */
  default List<String> lookupResources(
      SubjectRef subject, String resourceType, Permission permission, ReadConsistency consistency) {
    throw new UnsupportedOperationException("lookupResources is not supported");
  }

  default void writeRelationship(Relationship relationship) {
    throw new UnsupportedOperationException("relationship writes are not supported");
  }

  default void deleteRelationship(Relationship relationship) {
    throw new UnsupportedOperationException("relationship deletes are not supported");
  }
}
