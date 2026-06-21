package com.example.commerce.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import com.example.commerce.catalog.service.CatalogApplicationService;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InvalidTokenException;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import com.example.commerce.web.pagination.CursorPaginator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CommercePrincipalFilterTest {

  private static final String MUG_ID = "6801HWW000000";

  private final RecordingProductRepository repository = repository();
  private final CatalogApplicationService service = new CatalogApplicationService(
      repository,
      new ScopeAuthorizer(),
      new ResourceAuthorizer((subject, resource, permission) -> AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"))),
      new CursorPaginator(20, 100));

  @Test
  void anonymous_get_skips_jwt_validation() throws Exception {
    RecordingTokenValidator validator = new RecordingTokenValidator();
    MockMvc mockMvc = mockMvc(validator);

    mockMvc.perform(get("/api/catalog/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].id").value(MUG_ID));

    assertThat(validator.validateCalls()).isZero();
  }

  @Test
  void write_without_bearer_fails_before_controller() throws Exception {
    RecordingTokenValidator validator = new RecordingTokenValidator();
    MockMvc mockMvc = mockMvc(validator);

    mockMvc.perform(post("/api/catalog/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("missing bearer token"));

    assertThat(validator.validateCalls()).isZero();
  }

  @Test
  void write_with_invalid_bearer_fails_closed() throws Exception {
    RecordingTokenValidator validator = new RecordingTokenValidator();
    validator.failToken("bad-token");
    MockMvc mockMvc = mockMvc(validator);

    mockMvc.perform(post("/api/catalog/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("invalid bearer token"));
  }

  @Test
  void write_with_valid_bearer_sets_principal_for_scope_and_resource_gates() throws Exception {
    RecordingTokenValidator validator = new RecordingTokenValidator();
    validator.allowToken(
        "good-token",
        new CommercePrincipal("merchant", Set.of("catalog:write"), "fingerprint-merchant"));
    MockMvc mockMvc = mockMvc(validator);

    mockMvc.perform(post("/api/catalog/products")
            .header(HttpHeaders.AUTHORIZATION, "Bearer good-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("new-product"));
  }

  private MockMvc mockMvc(CommercePrincipalFilter.CatalogTokenValidator validator) {
    return MockMvcBuilders
        .standaloneSetup(new CatalogController(service, () -> "new-product"))
        .addFilters(new CommercePrincipalFilter(validator))
        .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
        .setValidator(validator())
        .build();
  }

  private static String createJson() {
    return """
        {
          "sku": "SKU-NEW",
          "name": "New Product",
          "price": 19.99,
          "inventoryStatus": "IN_STOCK"
        }
        """;
  }

  private static CommerceErrorProperties errorProperties() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/catalog");
    return properties;
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  private static RecordingProductRepository repository() {
    return new RecordingProductRepository(List.of(new Product(
        new ProductId(MUG_ID),
        new Sku("MUG-001"),
        new ProductName("Starter Mug"),
        Money.usd("12.50"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"))));
  }

  private static final class RecordingProductRepository implements ProductRepository {

    private final Map<ProductId, Product> products = new LinkedHashMap<>();

    private RecordingProductRepository(List<Product> initialProducts) {
      initialProducts.forEach(product -> products.put(product.id(), product));
    }

    @Override
    public List<Product> findAll() {
      return List.copyOf(products.values());
    }

    @Override
    public List<Product> findPage(@Nullable String afterId, int limit) {
      return products.values().stream()
          .sorted(Comparator.comparing(product -> product.id().value()))
          .filter(product -> afterId == null || product.id().value().compareTo(afterId) > 0)
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<Product> findById(ProductId productId) {
      return Optional.ofNullable(products.get(productId));
    }

    @Override
    public Product save(Product product) {
      products.put(product.id(), product);
      return product;
    }
  }

  private static final class RecordingTokenValidator
      implements CommercePrincipalFilter.CatalogTokenValidator {

    private final Map<String, CommercePrincipal> principals = new LinkedHashMap<>();
    private final Set<String> failingTokens = new java.util.HashSet<>();
    private int validateCalls;

    @Override
    public CommercePrincipal validate(String token) {
      validateCalls++;
      if (failingTokens.contains(token)) {
        throw new InvalidTokenException("bad token");
      }
      CommercePrincipal principal = principals.get(token);
      if (principal == null) {
        throw new InvalidTokenException("unknown token");
      }
      return principal;
    }

    void allowToken(String token, CommercePrincipal principal) {
      principals.put(token, principal);
    }

    void failToken(String token) {
      failingTokens.add(token);
    }

    int validateCalls() {
      return validateCalls;
    }
  }
}
