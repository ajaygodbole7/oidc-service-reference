package com.example.commerce.cart.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CartWebErrorHandlingTest {

  private final RecordingCartRepository repository = new RecordingCartRepository();
  private final CartApplicationService service = new CartApplicationService(
      repository,
      new ScopeAuthorizer(),
      new ResourceAuthorizer((subject, resource, permission) -> AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"))));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new CartController(service))
      .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
      .setValidator(validator())
      .build();

  @Test
  void rejects_unit_price_with_more_than_two_decimal_places_as_problem_json_400() throws Exception {
    mockMvc.perform(post("/api/cart/items")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productId": "sku-1",
                  "quantity": 1,
                  "unitPrice": 10.123
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"));

    org.assertj.core.api.Assertions.assertThat(repository.findByOwnerSubCalls()).isZero();
  }

  @Test
  void accepts_valid_add_item_json_request() throws Exception {
    mockMvc.perform(post("/api/cart/items")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productId": "sku-1",
                  "quantity": 1,
                  "unitPrice": 10.12
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.items[0].id").value("sku-1"));
  }

  @Test
  void missing_principal_request_attribute_returns_problem_json_401() throws Exception {
    mockMvc.perform(get("/api/cart"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));
  }

  @Test
  void unexpected_exceptions_return_problem_json_500_without_leaking_exception_detail() throws Exception {
    CommercePrincipal principal = principal();
    repository.failFindByOwnerSub();

    mockMvc.perform(get("/api/cart").requestAttr("commercePrincipal", principal))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Internal server error"))
        .andExpect(jsonPath("$.detail").value("unexpected error"));
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

  private static final class RecordingCartRepository implements CartRepository {

    private int findByOwnerSubCalls;
    private boolean failFindByOwnerSub;

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return Optional.empty();
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      findByOwnerSubCalls++;
      if (failFindByOwnerSub) {
        throw new IllegalStateException("database password leaked");
      }
      return Optional.of(new Cart(new CartId(ownerSub + "-cart"), ownerSub, List.of()));
    }

    @Override
    public Cart save(Cart cart) {
      return cart;
    }

    void failFindByOwnerSub() {
      failFindByOwnerSub = true;
    }

    int findByOwnerSubCalls() {
      return findByOwnerSubCalls;
    }
  }
}
