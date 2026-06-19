package com.example.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.authzed.api.v1.ObjectReference;
import com.authzed.api.v1.PermissionsServiceGrpc;
import com.authzed.api.v1.RelationshipUpdate;
import com.authzed.api.v1.SchemaServiceGrpc;
import com.authzed.api.v1.SubjectReference;
import com.authzed.api.v1.WriteRelationshipsRequest;
import com.authzed.api.v1.WriteSchemaRequest;
import com.authzed.grpcutil.BearerToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SpiceDbAuthorizationClientLiveTest {

  private static final String TARGET =
      System.getenv().getOrDefault("SPICEDB_TARGET", "127.0.0.1:50051");
  private static final String PRESHARED_KEY = System.getenv().getOrDefault(
      "SPICEDB_PRESHARED_KEY",
      "LOCAL_DEV_SPICEDB_PRESHARED_KEY__CHANGE_BEFORE_DEPLOY");
  private static final CommercePrincipal ALICE =
      new CommercePrincipal("alice", Set.of("cart:read", "cart:write"), "fingerprint");
  private static final ResourceRef ALICE_CART = new ResourceRef("cart", "alice-cart");
  private static final ResourceRef BOB_CART = new ResourceRef("cart", "bob-cart");
  private static final Permission READ = new Permission("read");

  @Test
  void real_spicedb_matches_fake_for_seeded_relationships() throws Exception {
    assumeTrue(Boolean.parseBoolean(System.getenv("SPICEDB_LIVE_TEST")),
        "set SPICEDB_LIVE_TEST=true to run against local SpiceDB");

    seedSpiceDb();

    InMemoryAuthorizationClient fake = new InMemoryAuthorizationClient()
        .grant(new Relationship(ALICE_CART, "read", SubjectRef.user("alice")))
        .grant(new Relationship(ALICE_CART, "write", SubjectRef.user("alice")))
        .grant(new Relationship(BOB_CART, "read", SubjectRef.user("bob")));

    try (SpiceDbAuthorizationClient real =
        new SpiceDbAuthorizationClient(TARGET, PRESHARED_KEY, true, 2_000)) {
      assertThat(real.check(SubjectRef.user("alice"), ALICE_CART, READ).allowed())
          .isEqualTo(fake.check(SubjectRef.user("alice"), ALICE_CART, READ).allowed());
      assertThat(real.check(SubjectRef.user("alice"), BOB_CART, READ).allowed())
          .isEqualTo(fake.check(SubjectRef.user("alice"), BOB_CART, READ).allowed());
    }
  }

  @Test
  void unavailable_spicedb_fails_closed_through_resource_authorizer() {
    try (SpiceDbAuthorizationClient unavailable =
        new SpiceDbAuthorizationClient("127.0.0.1:59999", PRESHARED_KEY, true, 200)) {
      ResourceAuthorizer authorizer = new ResourceAuthorizer(unavailable);

      assertThatThrownBy(() -> authorizer.requireAllowed(ALICE, ALICE_CART, READ))
          .isInstanceOf(AuthorizationDeniedException.class)
          .hasMessageContaining("unavailable");
    }
  }

  @Test
  void relationship_delete_and_touch_are_fully_consistent() throws Exception {
    assumeTrue(Boolean.parseBoolean(System.getenv("SPICEDB_LIVE_TEST")),
        "set SPICEDB_LIVE_TEST=true to run against local SpiceDB");

    seedSpiceDb();

    try (SpiceDbAuthorizationClient real =
        new SpiceDbAuthorizationClient(TARGET, PRESHARED_KEY, true, 2_000)) {
      assertThat(real.check(SubjectRef.user("alice"), ALICE_CART, READ).allowed()).isTrue();

      real.deleteRelationship(ALICE_CART, "owner", SubjectRef.user("alice"));
      assertThat(real.check(SubjectRef.user("alice"), ALICE_CART, READ).allowed()).isFalse();

      real.touchRelationship(ALICE_CART, "owner", SubjectRef.user("alice"));
      assertThat(real.check(SubjectRef.user("alice"), ALICE_CART, READ).allowed()).isTrue();
    }
  }

  private static void seedSpiceDb() throws IOException {
    ManagedChannel channel = ManagedChannelBuilder.forTarget(TARGET).usePlaintext().build();
    try {
      BearerToken bearerToken = new BearerToken(PRESHARED_KEY);
      SchemaServiceGrpc.SchemaServiceBlockingStub schema = SchemaServiceGrpc
          .newBlockingStub(channel)
          .withCallCredentials(bearerToken)
          .withDeadlineAfter(2, TimeUnit.SECONDS);
      PermissionsServiceGrpc.PermissionsServiceBlockingStub permissions = PermissionsServiceGrpc
          .newBlockingStub(channel)
          .withCallCredentials(bearerToken)
          .withDeadlineAfter(2, TimeUnit.SECONDS);

      schema.writeSchema(WriteSchemaRequest.newBuilder().setSchema(readSchema()).build());
      permissions.writeRelationships(WriteRelationshipsRequest.newBuilder()
          .addAllUpdates(seedRelationships())
          .build());
    } finally {
      channel.shutdownNow();
    }
  }

  private static String readSchema() throws IOException {
    Path path = Path.of("authorization-service/schema.zed");
    if (!Files.exists(path)) {
      path = Path.of("../authorization-service/schema.zed");
    }
    return Files.readString(path);
  }

  private static List<RelationshipUpdate> seedRelationships() {
    return List.of(
        touch(new ResourceRef("store", "main"), "manager", SubjectRef.user("merchant")),
        touch(ALICE_CART, "owner", SubjectRef.user("alice")),
        touch(BOB_CART, "owner", SubjectRef.user("bob")),
        touch(new ResourceRef("order", "alice-order"), "owner", SubjectRef.user("alice")),
        touch(new ResourceRef("order", "alice-order"), "support", SubjectRef.user("support")),
        touch(new ResourceRef("order", "bob-order"), "owner", SubjectRef.user("bob")));
  }

  private static RelationshipUpdate touch(
      ResourceRef resource, String relation, SubjectRef subject) {
    return RelationshipUpdate.newBuilder()
        .setOperation(RelationshipUpdate.Operation.OPERATION_TOUCH)
        .setRelationship(com.authzed.api.v1.Relationship.newBuilder()
            .setResource(ObjectReference.newBuilder()
                .setObjectType(resource.type())
                .setObjectId(resource.id()))
            .setRelation(relation)
            .setSubject(SubjectReference.newBuilder()
                .setObject(ObjectReference.newBuilder()
                    .setObjectType(subject.type())
                    .setObjectId(subject.id()))))
        .build();
  }
}
