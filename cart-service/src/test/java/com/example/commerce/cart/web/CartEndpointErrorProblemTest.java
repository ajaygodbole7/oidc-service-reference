package com.example.commerce.cart.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartItem;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.domain.Quantity;
import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * RFC 9457 ProblemDetail web tests for the remaining {@link CartController} endpoint error cases,
 * routed through the starter's auto-configured {@link GlobalExceptionHandler}: a denied resource
 * check on {@code GET /api/carts/{cartId}}, a remove of a product not in the cart on
 * {@code DELETE /api/cart/items/{productId}}, and bean-validation failures on
 * {@code POST /api/cart/items}. Each asserts the full problem shape (status, content-type, type,
 * title, detail, errorCode) and that no SpiceDB trace / exception internals leak into the body.
 */
class CartEndpointErrorProblemTest {

  /** A SpiceDB-style decision trace string that must never reach the response body. */
  private static final String SPICEDB_TRACE_SENTINEL = "spicedb-trace:cart#read@user:mallory-DENIED";

  // ---- GET /api/carts/{cartId} : resource authorizer DENIES -> 403 scrubbed

  @Test
  void get_other_users_cart_when_resource_check_denies_returns_rfc9457_403_without_trace_leak()
      throws Exception {
    AuthorizationClient denying = (subject, resource, permission) -> AuthorizationDecision.deny(
        DecisionTrace.resource(false, subject, resource, permission, SPICEDB_TRACE_SENTINEL));
    MockMvc mockMvc = mockMvc(new EmptyCartRepository(), new ResourceAuthorizer(denying));

    MvcResult result = mockMvc.perform(
            get("/api/carts/{cartId}", "someone-elses-cart").requestAttr("commercePrincipal", principal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/cart/authorization-denied"))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.detail").value("resource authorization denied"))
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"))
        .andReturn();

    // The SpiceDB decision trace and the denied subject must never reach the body. The RFC 9457
    // "instance" field legitimately echoes the caller's own request path, so the cart id is not
    // asserted absent here.
    String body = result.getResponse().getContentAsString();
    Assertions.assertThat(body)
        .doesNotContain(SPICEDB_TRACE_SENTINEL)
        .doesNotContain("mallory");
  }

  // ---- DELETE /api/cart/items/{productId} : product not in cart -> domain IAE -> 400 scrubbed

  @Test
  void remove_product_not_in_cart_returns_rfc9457_400_invalid_request() throws Exception {
    Cart cartWithoutProduct = new Cart(
        new CartId("alice-cart"),
        "alice",
        List.of(new CartItem(new ProductId("sku-present"), new Quantity(1), new Money(new BigDecimal("10.00"), "USD"))));
    MockMvc mockMvc = mockMvc(new FixedOwnerCartRepository(cartWithoutProduct), allowingAuthorizer());

    mockMvc.perform(
            delete("/api/cart/items/{productId}", "sku-absent").requestAttr("commercePrincipal", principal()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/cart/invalid-request"))
        .andExpect(jsonPath("$.title").value("Bad request"))
        .andExpect(jsonPath("$.detail").value("invalid request"))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
  }

  // ---- POST /api/cart/items : missing/blank required field -> 400 bean-validation problem

  @Test
  void add_item_with_blank_product_id_returns_rfc9457_400_validation_problem() throws Exception {
    MockMvc mockMvc = mockMvc(new EmptyCartRepository(), allowingAuthorizer());

    mockMvc.perform(post("/api/cart/items")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productId": "",
                  "quantity": 1,
                  "unitPrice": 10.00
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/cart/validation-failed"))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  }

  @Test
  void add_item_with_missing_unit_price_returns_rfc9457_400_validation_problem() throws Exception {
    MockMvc mockMvc = mockMvc(new EmptyCartRepository(), allowingAuthorizer());

    mockMvc.perform(post("/api/cart/items")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productId": "sku-1",
                  "quantity": 1
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/cart/validation-failed"))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
  }

  // ---- helpers

  private static MockMvc mockMvc(CartRepository repository, ResourceAuthorizer resourceAuthorizer) {
    CartApplicationService service =
        new CartApplicationService(repository, new ScopeAuthorizer(), resourceAuthorizer);
    return MockMvcBuilders
        .standaloneSetup(new CartController(service))
        .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
        .setValidator(validator())
        .build();
  }

  private static ResourceAuthorizer allowingAuthorizer() {
    AuthorizationClient allowing = (subject, resource, permission) -> AuthorizationDecision.allow(
        DecisionTrace.resource(true, subject, resource, permission, "relationship_found"));
    return new ResourceAuthorizer(allowing);
  }

  private static CommercePrincipal principal() {
    return new CommercePrincipal("alice", Set.of("cart:read", "cart:write"), "fingerprint-alice");
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  private static CommerceErrorProperties errorProperties() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/cart");
    return properties;
  }

  /** Repository whose {@code findById} returns nothing; the deny fires before the lookup matters. */
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

  /** Returns a fixed cart for the current owner so {@code removeItem} reaches the domain guard. */
  private static final class FixedOwnerCartRepository implements CartRepository {

    private final Cart cart;

    FixedOwnerCartRepository(Cart cart) {
      this.cart = cart;
    }

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return Optional.of(cart);
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      return Optional.of(cart);
    }

    @Override
    public Cart save(Cart savedCart) {
      return savedCart;
    }
  }
}
