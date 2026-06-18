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
  void resource_authorizer_fails_closed_when_client_unavailable() {
    AuthorizationClient unavailable = (subject, resource, permission) -> {
      throw new AuthorizationUnavailableException("SpiceDB unavailable");
    };

    assertThatThrownBy(() -> new ResourceAuthorizer(unavailable)
        .requireAllowed(ALICE, CART, new Permission("read")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("unavailable")
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("authorization_unavailable");
        });
  }

  @Test
  void fake_can_accept_relationship_seed_shape() {
    InMemoryAuthorizationClient client = new InMemoryAuthorizationClient()
        .grant(new Relationship(CART, "read", SubjectRef.user("alice")));

    AuthorizationDecision decision =
        client.check(SubjectRef.user("alice"), CART, new Permission("read"));

    assertThat(decision.allowed()).isTrue();
  }
}
