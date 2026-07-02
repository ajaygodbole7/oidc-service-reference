package com.example.commerce.security;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.LookupPermissionship;
import com.authzed.api.v1.LookupResourcesRequest;
import com.authzed.api.v1.LookupResourcesResponse;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.RelationshipUpdate;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteRelationshipsRequest;
import com.authzed.api.v1.WriteRelationshipsResponse;
import com.authzed.api.v1.ZedToken;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Real SpiceDB adapter for gate 4. The SDK is contained here; services depend on the
 * AuthorizationClient port and ResourceAuthorizer.
 */
public final class SpiceDbAuthorizationClient implements AuthorizationClient, AutoCloseable {

  private final ManagedChannel channel;
  private final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
  private final long deadlineMillis;
  // High-water ZedToken from this process's most recent relationship write. READ_YOUR_WRITES reads
  // pin at_least_as_fresh to it, so a just-written grant is visible on the next read without paying
  // full consistency on every call. In-memory per instance: a fresh process falls back to
  // minimize_latency until its first write (documented as a multi-instance hardening note).
  private final AtomicReference<@Nullable String> highWaterToken = new AtomicReference<>();

  public SpiceDbAuthorizationClient(String target, String presharedKey, boolean plaintext) {
    this(target, presharedKey, plaintext, 2_000);
  }

  public void touchRelationship(ResourceRef resource, String relation, SubjectRef subject) {
    applyRelationship(RelationshipUpdate.Operation.OPERATION_TOUCH, resource, relation, subject);
  }

  public void deleteRelationship(ResourceRef resource, String relation, SubjectRef subject) {
    applyRelationship(RelationshipUpdate.Operation.OPERATION_DELETE, resource, relation, subject);
  }

  @Override
  public void writeRelationship(Relationship relationship) {
    touchRelationship(relationship.resource(), relationship.relation(), relationship.subject());
  }

  @Override
  public void deleteRelationship(Relationship relationship) {
    deleteRelationship(relationship.resource(), relationship.relation(), relationship.subject());
  }

  public SpiceDbAuthorizationClient(
      String target, String presharedKey, boolean plaintext, long deadlineMillis) {
    ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
    this.channel = plaintext ? builder.usePlaintext().build() : builder.useTransportSecurity().build();
    this.permissions = PermissionsServiceGrpc.newBlockingStub(channel)
        .withCallCredentials(new BearerToken(presharedKey));
    this.deadlineMillis = deadlineMillis;
  }

  @Override
  public AuthorizationDecision check(
      SubjectRef subject, ResourceRef resource, Permission permission) {
    return check(subject, resource, permission, ReadConsistency.FULLY_CONSISTENT);
  }

  @Override
  public AuthorizationDecision check(
      SubjectRef subject, ResourceRef resource, Permission permission, ReadConsistency consistency) {
    try {
      CheckPermissionRequest request = CheckPermissionRequest.newBuilder()
          .setConsistency(consistency(consistency))
          .setResource(object(resource))
          .setSubject(SubjectReference.newBuilder().setObject(object(subject)).build())
          .setPermission(permission.value())
          .build();
      CheckPermissionResponse response = permissions
          .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
          .checkPermission(request);
      boolean allowed = response.getPermissionship()
          == CheckPermissionResponse.Permissionship.PERMISSIONSHIP_HAS_PERMISSION;
      DecisionTrace trace = DecisionTrace.resource(
          allowed,
          subject,
          resource,
          permission,
          allowed ? "relationship_found" : "relationship_missing");
      return allowed ? AuthorizationDecision.allow(trace) : AuthorizationDecision.deny(trace);
    } catch (RuntimeException e) {
      throw new AuthorizationUnavailableException("SpiceDB unavailable", e);
    }
  }

  @Override
  public List<String> lookupResources(
      SubjectRef subject, String resourceType, Permission permission, ReadConsistency consistency) {
    try {
      LookupResourcesRequest request = LookupResourcesRequest.newBuilder()
          .setConsistency(consistency(consistency))
          .setResourceObjectType(resourceType)
          .setPermission(permission.value())
          .setSubject(SubjectReference.newBuilder().setObject(object(subject)).build())
          .build();
      Iterator<LookupResourcesResponse> responses = permissions
          .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
          .lookupResources(request);
      List<String> resourceIds = new ArrayList<>();
      while (responses.hasNext()) {
        LookupResourcesResponse response = responses.next();
        // Only fully-resolved permissions count; conditional results (caveats) are not used here.
        if (response.getPermissionship()
            == LookupPermissionship.LOOKUP_PERMISSIONSHIP_HAS_PERMISSION) {
          resourceIds.add(response.getResourceObjectId());
        }
      }
      return List.copyOf(resourceIds);
    } catch (RuntimeException e) {
      throw new AuthorizationUnavailableException("SpiceDB unavailable", e);
    }
  }

  /** Map the SDK-free requirement to a SpiceDB Consistency, supplying our own high-water ZedToken. */
  private Consistency consistency(ReadConsistency consistency) {
    return switch (consistency) {
      case FULLY_CONSISTENT -> Consistency.newBuilder().setFullyConsistent(true).build();
      case MINIMIZE_LATENCY -> Consistency.newBuilder().setMinimizeLatency(true).build();
      case READ_YOUR_WRITES -> {
        String token = highWaterToken.get();
        yield (token == null || token.isBlank())
            ? Consistency.newBuilder().setMinimizeLatency(true).build()
            : Consistency.newBuilder()
                .setAtLeastAsFresh(ZedToken.newBuilder().setToken(token).build())
                .build();
      }
    };
  }

  @Override
  public void close() {
    channel.shutdown();
    try {
      if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        channel.shutdownNow();
      }
    } catch (InterruptedException e) {
      channel.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static ObjectReference object(ResourceRef resource) {
    return ObjectReference.newBuilder()
        .setObjectType(resource.type())
        .setObjectId(resource.id())
        .build();
  }

  private static ObjectReference object(SubjectRef subject) {
    return ObjectReference.newBuilder()
        .setObjectType(subject.type())
        .setObjectId(subject.id())
        .build();
  }

  private void applyRelationship(
      RelationshipUpdate.Operation operation,
      ResourceRef resource,
      String relation,
      SubjectRef subject) {
    try {
      WriteRelationshipsResponse response = permissions
          .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
          .writeRelationships(WriteRelationshipsRequest.newBuilder()
              .addUpdates(RelationshipUpdate.newBuilder()
                  .setOperation(operation)
                  .setRelationship(com.authzed.api.v1.Relationship.newBuilder()
                      .setResource(object(resource))
                      .setRelation(relation)
                      .setSubject(SubjectReference.newBuilder().setObject(object(subject)))))
              .build());
      // Advance the read-your-writes high-water mark to this write's revision.
      if (response.hasWrittenAt()) {
        highWaterToken.set(response.getWrittenAt().getToken());
      }
    } catch (RuntimeException e) {
      throw new AuthorizationUnavailableException("SpiceDB unavailable", e);
    }
  }
}
