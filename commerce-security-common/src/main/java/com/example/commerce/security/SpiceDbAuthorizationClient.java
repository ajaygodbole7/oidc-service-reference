package com.example.commerce.security;

import com.authzed.api.v1.CheckPermissionRequest;
import com.authzed.api.v1.CheckPermissionResponse;
import com.authzed.api.v1.Consistency;
import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.RelationshipUpdate;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteRelationshipsRequest;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

/**
 * Real SpiceDB adapter for gate 4. The SDK is contained here; services depend on the
 * AuthorizationClient port and ResourceAuthorizer.
 */
public final class SpiceDbAuthorizationClient implements AuthorizationClient, AutoCloseable {

  private final ManagedChannel channel;
  private final PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions;
  private final long deadlineMillis;

  public SpiceDbAuthorizationClient(String target, String presharedKey, boolean plaintext) {
    this(target, presharedKey, plaintext, 2_000);
  }

  public void touchRelationship(ResourceRef resource, String relation, SubjectRef subject) {
    writeRelationship(RelationshipUpdate.Operation.OPERATION_TOUCH, resource, relation, subject);
  }

  public void deleteRelationship(ResourceRef resource, String relation, SubjectRef subject) {
    writeRelationship(RelationshipUpdate.Operation.OPERATION_DELETE, resource, relation, subject);
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
    try {
      CheckPermissionRequest request = CheckPermissionRequest.newBuilder()
          .setConsistency(Consistency.newBuilder().setFullyConsistent(true).build())
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

  private void writeRelationship(
      RelationshipUpdate.Operation operation,
      ResourceRef resource,
      String relation,
      SubjectRef subject) {
    try {
      permissions
          .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS)
          .writeRelationships(WriteRelationshipsRequest.newBuilder()
              .addUpdates(RelationshipUpdate.newBuilder()
                  .setOperation(operation)
                  .setRelationship(com.authzed.api.v1.Relationship.newBuilder()
                      .setResource(object(resource))
                      .setRelation(relation)
                      .setSubject(SubjectReference.newBuilder().setObject(object(subject)))))
              .build());
    } catch (RuntimeException e) {
      throw new AuthorizationUnavailableException("SpiceDB unavailable", e);
    }
  }
}
