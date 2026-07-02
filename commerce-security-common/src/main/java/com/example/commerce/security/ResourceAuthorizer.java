package com.example.commerce.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

  /**
   * Batch variant for collections: issues one check per resource concurrently via virtual threads,
   * preserving result order. Fails closed (throws) if SpiceDB is unavailable for any resource.
   */
  public List<Optional<DecisionTrace>> filterAllowed(
      CommercePrincipal principal, List<ResourceRef> resources, Permission permission) {
    if (resources.isEmpty()) {
      return List.of();
    }
    try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Optional<DecisionTrace>>> futures = resources.stream()
          .map(resource -> exec.submit(() -> filterAllowed(principal, resource, permission)))
          .toList();
      List<Optional<DecisionTrace>> results = new ArrayList<>(resources.size());
      for (var future : futures) {
        try {
          results.add(future.get());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          DecisionTrace trace = DecisionTrace.resource(
              false, SubjectRef.user(principal.subject()),
              resources.getFirst(), permission, "interrupted");
          throw new AuthorizationDeniedException("authorization check interrupted", trace, e);
        } catch (ExecutionException e) {
          if (e.getCause() instanceof AuthorizationDeniedException ade) throw ade;
          throw new RuntimeException("authorization check failed", e.getCause());
        }
      }
      return Collections.unmodifiableList(results);
    }
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

  /**
   * List gate via SpiceDB LookupResources: returns the ids of {@code resourceType} the subject may
   * access under {@code permission}, plus one bounded trace for the whole lookup. SpiceDB is the
   * authority for the set; callers page the returned ids in their own store. Fails closed (throws)
   * when SpiceDB is unavailable, mirroring requireAllowed.
   */
  public AllowedResourceIds lookupAllowedResourceIds(
      CommercePrincipal principal,
      String resourceType,
      Permission permission,
      ReadConsistency consistency) {
    SubjectRef subject = SubjectRef.user(principal.subject());
    try {
      List<String> ids = client.lookupResources(subject, resourceType, permission, consistency);
      DecisionTrace trace = DecisionTrace.resource(
          true, subject, new ResourceRef(resourceType, "*"), permission, "lookup_resources");
      return new AllowedResourceIds(ids, trace);
    } catch (AuthorizationUnavailableException e) {
      DecisionTrace trace = DecisionTrace.resource(
          false, subject, new ResourceRef(resourceType, "*"), permission, "authorization_unavailable");
      throw new AuthorizationDeniedException("resource authorization unavailable", trace, e);
    }
  }

  /** Result of a LookupResources gate: the allowed resource ids and one bounded evidence trace. */
  public record AllowedResourceIds(List<String> ids, DecisionTrace trace) {}
}
