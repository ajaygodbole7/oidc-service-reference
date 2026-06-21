package com.example.commerce.cart.testfixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.InMemoryAuthorizationClient;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CartTestFixtureControllerTest {

  @Test
  void default_profile_does_not_register_test_fixture_controller() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(CartRelationshipFixture.class, RecordingFixture::new);
      context.register(CartTestFixtureController.class);
      context.refresh();

      assertThat(context.getBeansOfType(CartTestFixtureController.class)).isEmpty();
    }
  }

  @Test
  void test_fixture_profile_registers_test_fixture_controller() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.getEnvironment().setActiveProfiles("test-fixture");
      context.registerBean(CartRelationshipFixture.class, RecordingFixture::new);
      context.registerBean(CartApplicationService.class, CartTestFixtureControllerTest::cartService);
      context.register(CartTestFixtureController.class);
      context.refresh();

      assertThat(context.getBeansOfType(CartTestFixtureController.class)).hasSize(1);
    }
  }

  @Test
  void restore_local_seed_returns_bounded_relationship_evidence() throws Exception {
    RecordingFixture fixture = new RecordingFixture();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new CartTestFixtureController(fixture, cartService()))
        .build();

    mockMvc.perform(post("/api/_test/cart/relationships/local-seed"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("restored"))
        .andExpect(jsonPath("$.relationships[0]").value("cart:alice-cart#owner@user:alice"))
        .andExpect(jsonPath("$.relationships[1]").value("cart:bob-cart#owner@user:bob"));

    assertThat(fixture.actions()).containsExactly("restoreLocalSeed");
  }

  @Test
  void remove_alice_owner_returns_only_the_mutated_fixture_relationship() throws Exception {
    RecordingFixture fixture = new RecordingFixture();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new CartTestFixtureController(fixture, cartService()))
        .build();

    mockMvc.perform(delete("/api/_test/cart/relationships/alice-cart-owner"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("removed"))
        .andExpect(jsonPath("$.relationships[0]").value("cart:alice-cart#owner@user:alice"));

    assertThat(fixture.actions()).containsExactly("removeAliceOwner");
  }

  @Test
  void restore_alice_owner_returns_only_the_mutated_fixture_relationship() throws Exception {
    RecordingFixture fixture = new RecordingFixture();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new CartTestFixtureController(fixture, cartService()))
        .build();

    mockMvc.perform(post("/api/_test/cart/relationships/alice-cart-owner"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("restored"))
        .andExpect(jsonPath("$.relationships[0]").value("cart:alice-cart#owner@user:alice"));

    assertThat(fixture.actions()).containsExactly("restoreAliceOwner");
  }

  @Test
  void evidence_returns_bounded_principal_and_header_state() throws Exception {
    RecordingFixture fixture = new RecordingFixture();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new CartTestFixtureController(fixture, cartService()))
        .build();
    CommercePrincipal principal =
        new CommercePrincipal("alice", Set.of("cart:read"), "token-fingerprint");

    mockMvc.perform(get("/api/_test/cart/evidence")
            .requestAttr("commercePrincipal", principal)
            .header(HttpHeaders.AUTHORIZATION, "Bearer gateway-injected")
            .header("X-Trace-Id", "trace-test-1")
            .header("X-User", "admin"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.traceId").value("trace-test-1"))
        .andExpect(jsonPath("$.request").value("GET /api/_test/cart/evidence"))
        .andExpect(jsonPath("$.subject").value("alice"))
        .andExpect(jsonPath("$.tokenFingerprint").value("token-fingerprint"))
        .andExpect(jsonPath("$.authorizationBearerPresent").value(true))
        .andExpect(jsonPath("$.unsafeIdentityHeaderPresent").value(true))
        .andExpect(jsonPath("$.sessionResolved").value(true))
        .andExpect(jsonPath("$.serviceJwtValidated").value(true))
        .andExpect(jsonPath("$.serviceTraces[0].gate").value("scope"))
        .andExpect(jsonPath("$.serviceTraces[0].evidence.scope").value("cart:read"))
        .andExpect(jsonPath("$.serviceTraces[1].gate").value("resource"))
        .andExpect(jsonPath("$.serviceTraces[1].evidence.resource").value("cart:alice-cart"))
        .andExpect(jsonPath("$.serviceTraces[1].evidence.permission").value("read"));
  }

  private static CartApplicationService cartService() {
    InMemoryAuthorizationClient authorizationClient = new InMemoryAuthorizationClient()
        .grant(new Relationship(
            new ResourceRef("cart", "alice-cart"),
            "owner",
            SubjectRef.user("alice")));
    return new CartApplicationService(
        new SeededCartRepository(),
        new ScopeAuthorizer(),
        new ResourceAuthorizer(authorizationClient));
  }

  private static final class RecordingFixture implements CartRelationshipFixture {

    private final List<String> actions = new ArrayList<>();

    @Override
    public void restoreLocalSeed() {
      actions.add("restoreLocalSeed");
    }

    @Override
    public void restoreAliceOwner() {
      actions.add("restoreAliceOwner");
    }

    @Override
    public void removeAliceOwner() {
      actions.add("removeAliceOwner");
    }

    List<String> actions() {
      return List.copyOf(actions);
    }
  }

  private static final class SeededCartRepository implements CartRepository {

    private Cart aliceCart = new Cart(new CartId("alice-cart"), "alice", List.of());

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return aliceCart.id().equals(cartId) ? Optional.of(aliceCart.copy()) : Optional.empty();
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      return "alice".equals(ownerSub) ? Optional.of(aliceCart.copy()) : Optional.empty();
    }

    @Override
    public Cart save(Cart cart) {
      aliceCart = cart.copy();
      return aliceCart.copy();
    }
  }
}
