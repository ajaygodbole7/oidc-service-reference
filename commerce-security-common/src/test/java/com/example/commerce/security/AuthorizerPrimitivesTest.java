package com.example.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizerPrimitivesTest {

  private static final CommercePrincipal ALICE =
      new CommercePrincipal("alice", Set.of("cart:read", "cart:write"), "abc123def4567890");
  private static final ResourceRef CART = new ResourceRef("cart", "alice-cart");

  @Test
  void scope_authorizer_allows_present_scope_and_traces_fingerprint_not_token() {
    DecisionTrace trace = new ScopeAuthorizer().requireScope(ALICE, "cart:write");

    assertThat(trace.allowed()).isTrue();
    assertThat(trace.gate()).isEqualTo("scope");
    assertThat(trace.reason()).isEqualTo("scope_present");
    assertThat(trace.evidence())
        .containsEntry("subject", "alice")
        .containsEntry("scope", "cart:write")
        .containsEntry("token_fingerprint", "abc123def4567890");
  }

  @Test
  void scope_authorizer_denies_missing_scope() {
    ScopeAuthorizer authorizer = new ScopeAuthorizer();

    assertThatThrownBy(() -> authorizer.requireScope(ALICE, "orders:write"))
        .isInstanceOf(AuthorizationDeniedException.class)
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("scope_missing");
        });
  }

  @Test
  void resource_authorizer_allows_fake_grant() {
    InMemoryAuthorizationClient client = new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("alice"), CART, new Permission("write"));
    ResourceAuthorizer authorizer = new ResourceAuthorizer(client);

    DecisionTrace trace = authorizer.requireAllowed(ALICE, CART, new Permission("write"));

    assertThat(trace.allowed()).isTrue();
    assertThat(trace.gate()).isEqualTo("resource");
    assertThat(trace.evidence())
        .containsEntry("subject", "user:alice")
        .containsEntry("resource", "cart:alice-cart")
        .containsEntry("permission", "write");
  }

  @Test
  void resource_authorizer_denies_missing_relationship() {
    ResourceAuthorizer authorizer = new ResourceAuthorizer(new InMemoryAuthorizationClient());

    assertThatThrownBy(() -> authorizer.requireAllowed(ALICE, CART, new Permission("write")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("denied")
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("relationship_missing");
        });
  }

  @Test
  void resource_authorizer_fails_closed_when_client_unavailable_and_preserves_cause() {
    RuntimeException grpcFailure = new RuntimeException("connection refused");
    AuthorizationClient unavailable = (subject, resource, permission) -> {
      throw new AuthorizationUnavailableException("SpiceDB unavailable", grpcFailure);
    };

    assertThatThrownBy(() -> new ResourceAuthorizer(unavailable)
        .requireAllowed(ALICE, CART, new Permission("read")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("unavailable")
        .satisfies(thrown -> {
          assertThat(thrown)
              .hasCauseInstanceOf(AuthorizationUnavailableException.class);
          assertThat(thrown.getCause())
              .hasCause(grpcFailure);
          DecisionTrace decision = ((AuthorizationDeniedException) thrown).trace();
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("authorization_unavailable");
        });
  }

  @Test
  void resource_authorizer_does_not_relabel_programming_bugs_as_unavailable() {
    AuthorizationClient buggy = (subject, resource, permission) -> {
      throw new IllegalStateException("mapping bug");
    };

    assertThatThrownBy(() -> new ResourceAuthorizer(buggy)
        .requireAllowed(ALICE, CART, new Permission("read")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("mapping bug");
  }

  @Test
  void fake_can_accept_relationship_seed_shape() {
    InMemoryAuthorizationClient client = new InMemoryAuthorizationClient()
        .grant(new Relationship(CART, "read", SubjectRef.user("alice")));

    AuthorizationDecision decision =
        client.check(SubjectRef.user("alice"), CART, new Permission("read"));

    assertThat(decision.allowed()).isTrue();
  }

  @Test
  void fake_write_relationship_uses_schema_permissions_and_delete_revokes_them() {
    InMemoryAuthorizationClient client = new InMemoryAuthorizationClient();
    Relationship owner = new Relationship(CART, "owner", SubjectRef.user("alice"));

    client.writeRelationship(owner);

    assertThat(client.check(SubjectRef.user("alice"), CART, new Permission("read")).allowed())
        .isTrue();
    assertThat(client.check(SubjectRef.user("alice"), CART, new Permission("write")).allowed())
        .isTrue();

    client.deleteRelationship(owner);

    assertThat(client.check(SubjectRef.user("alice"), CART, new Permission("read")).allowed())
        .isFalse();
    assertThat(client.check(SubjectRef.user("alice"), CART, new Permission("write")).allowed())
        .isFalse();
  }

  @Test
  void resource_authorizer_writes_relationship_through_port_with_bounded_trace() {
    InMemoryAuthorizationClient client = new InMemoryAuthorizationClient();
    ResourceAuthorizer authorizer = new ResourceAuthorizer(client);

    DecisionTrace trace = authorizer.writeRelationship(
        new Relationship(CART, "owner", SubjectRef.user("alice")));

    assertThat(trace.gate()).isEqualTo("resource_provisioning");
    assertThat(trace.allowed()).isTrue();
    assertThat(trace.reason()).isEqualTo("relationship_written");
    assertThat(trace.evidence())
        .containsEntry("subject", "user:alice")
        .containsEntry("resource", "cart:alice-cart")
        .containsEntry("relation", "owner");
    assertThat(client.check(SubjectRef.user("alice"), CART, new Permission("write")).allowed())
        .isTrue();
  }

  @Test
  void resource_authorizer_fails_closed_when_relationship_write_unavailable() {
    AuthorizationClient unavailable = new AuthorizationClient() {
      @Override
      public AuthorizationDecision check(
          SubjectRef subject, ResourceRef resource, Permission permission) {
        return AuthorizationDecision.deny(DecisionTrace.resource(
            false, subject, resource, permission, "relationship_missing"));
      }

      @Override
      public void writeRelationship(Relationship relationship) {
        throw new AuthorizationUnavailableException("SpiceDB unavailable");
      }
    };

    assertThatThrownBy(() -> new ResourceAuthorizer(unavailable)
        .writeRelationship(new Relationship(CART, "owner", SubjectRef.user("alice"))))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("unavailable")
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.gate()).isEqualTo("resource_provisioning");
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("authorization_unavailable");
        });
  }
}
