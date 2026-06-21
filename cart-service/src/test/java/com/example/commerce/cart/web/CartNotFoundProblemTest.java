package com.example.commerce.cart.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Proves a {@link com.example.commerce.cart.service.CartNotFoundException} is mapped to an RFC 9457
 * 404 by the starter's auto-configured {@link GlobalExceptionHandler} (no cart-local advice): the
 * problem {@code type} is {@code <base-url>/cart-not-found} and {@code errorCode} is
 * {@code CART_NOT_FOUND}.
 */
class CartNotFoundProblemTest {

  private final CartApplicationService service = new CartApplicationService(
      new EmptyCartRepository(),
      new ScopeAuthorizer(),
      new ResourceAuthorizer((subject, resource, permission) -> AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"))));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new CartController(service))
      .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
      .build();

  @Test
  void missing_current_cart_returns_rfc9457_404_with_cart_not_found_slug() throws Exception {
    mockMvc.perform(get("/api/cart").requestAttr("commercePrincipal", principal()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/cart/cart-not-found"))
        .andExpect(jsonPath("$.errorCode").value("CART_NOT_FOUND"));
  }

  private static CommercePrincipal principal() {
    return new CommercePrincipal("alice", Set.of("cart:read"), "fingerprint-alice");
  }

  private static CommerceErrorProperties errorProperties() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/cart");
    return properties;
  }

  /** Always empty, so getCurrentCart throws CartNotFoundException after the scope check passes. */
  private static final class EmptyCartRepository implements CartRepository {

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return Optional.empty();
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      return Optional.empty();
    }

    @Override
    public Cart save(Cart cart) {
      return cart;
    }
  }
}
