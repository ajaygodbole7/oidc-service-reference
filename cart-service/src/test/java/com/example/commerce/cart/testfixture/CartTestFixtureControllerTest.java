package com.example.commerce.cart.testfixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.security.CommercePrincipal;
import java.util.ArrayList;
import java.util.List;
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
      context.register(CartTestFixtureController.class);
      context.refresh();

      assertThat(context.getBeansOfType(CartTestFixtureController.class)).hasSize(1);
    }
  }

  @Test
  void restore_local_seed_returns_bounded_relationship_evidence() throws Exception {
    RecordingFixture fixture = new RecordingFixture();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new CartTestFixtureController(fixture))
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
        .standaloneSetup(new CartTestFixtureController(fixture))
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
        .standaloneSetup(new CartTestFixtureController(fixture))
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
        .standaloneSetup(new CartTestFixtureController(fixture))
        .build();
    CommercePrincipal principal =
        new CommercePrincipal("alice", Set.of("cart:read"), "token-fingerprint");

    mockMvc.perform(get("/api/_test/cart/evidence")
            .requestAttr("commercePrincipal", principal)
            .header(HttpHeaders.AUTHORIZATION, "Bearer gateway-injected")
            .header("X-User", "admin"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.subject").value("alice"))
        .andExpect(jsonPath("$.tokenFingerprint").value("token-fingerprint"))
        .andExpect(jsonPath("$.authorizationBearerPresent").value(true))
        .andExpect(jsonPath("$.unsafeIdentityHeaderPresent").value(true));
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
}
