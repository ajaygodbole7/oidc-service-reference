package com.example.commerce.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CatalogWebTest {

  private static final String MUG_ID = "6801HWW000000";
  private static final String BAG_ID = "6801HWW00YGJ3";

  private final RecordingProductRepository repository = repository();
  private final CatalogApplicationService service = new CatalogApplicationService(
      repository,
      new ScopeAuthorizer(),
      new ResourceAuthorizer((subject, resource, permission) -> AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"))),
      new CursorPaginator(20, 100));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new CatalogController(service, () -> "minted-product"))
      .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
      .setValidator(validator())
      .build();

  @Test
  void anonymous_list_read_returns_public_product_projection() throws Exception {
    mockMvc.perform(get("/api/catalog/products"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.products[0].id").value(MUG_ID))
        .andExpect(jsonPath("$.products[0].storeId").doesNotExist());
  }

  @Test
  void anonymous_detail_read_returns_public_product_projection() throws Exception {
    mockMvc.perform(get("/api/catalog/products/" + MUG_ID))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(MUG_ID))
        .andExpect(jsonPath("$.storeId").doesNotExist());
  }

  @Test
  void unknown_product_returns_404_problem_json() throws Exception {
    mockMvc.perform(get("/api/catalog/products/6801HWWZZZZZZ"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.detail").value("no product with id 6801HWWZZZZZZ"))
        .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/product-not-found"));
  }

  @Test
  void list_caps_page_size_and_returns_next_cursor() throws Exception {
    mockMvc.perform(get("/api/catalog/products").param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products.length()").value(1))
        .andExpect(jsonPath("$.products[0].id").value(MUG_ID))
        .andExpect(jsonPath("$.nextCursor").value(CursorPaginator.encodeCursor(MUG_ID)));
  }

  @Test
  void list_following_cursor_returns_next_page() throws Exception {
    mockMvc.perform(get("/api/catalog/products")
            .param("limit", "1")
            .param("cursor", CursorPaginator.encodeCursor(MUG_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].id").value(BAG_ID));
  }

  @Test
  void list_last_page_has_null_next_cursor() throws Exception {
    mockMvc.perform(get("/api/catalog/products").param("limit", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products.length()").value(2))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void missing_principal_on_write_returns_problem_json_401() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.detail").value("missing authenticated principal"))
        .andExpect(jsonPath("$.errorCode").value("MISSING_PRINCIPAL"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/missing-principal"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void create_product_mints_tsid_and_returns_created_public_projection() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("minted-product"))
        .andExpect(jsonPath("$.storeId").doesNotExist());
  }

  @Test
  void put_product_with_principal_replaces_public_projection() throws Exception {
    mockMvc.perform(put("/api/catalog/products/" + MUG_ID)
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Updated Mug",
                  "price": 13.00,
                  "inventoryStatus": "LOW_STOCK"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(MUG_ID))
        .andExpect(jsonPath("$.name").value("Updated Mug"))
        .andExpect(jsonPath("$.inventoryStatus").value("LOW_STOCK"));
  }

  @Test
  void create_with_invalid_body_returns_400_validation_problem_json() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidCreateJson()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/validation-failed"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void update_with_invalid_body_returns_400_validation_problem_json() throws Exception {
    mockMvc.perform(put("/api/catalog/products/" + MUG_ID)
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidUpdateJson()))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/validation-failed"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void create_without_write_scope_returns_403_problem_json() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .requestAttr("commercePrincipal", nonWriterPrincipal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.detail").value("missing required scope catalog:write"))
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/authorization-denied"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void update_without_write_scope_returns_403_problem_json() throws Exception {
    mockMvc.perform(put("/api/catalog/products/" + MUG_ID)
            .requestAttr("commercePrincipal", nonWriterPrincipal())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Updated Mug",
                  "price": 13.00,
                  "inventoryStatus": "LOW_STOCK"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"))
        .andExpect(jsonPath("$.detail").value("missing required scope catalog:write"))
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/authorization-denied"));

    assertThat(repository.saveCount()).isZero();
  }

  private static String invalidCreateJson() {
    return """
        {
          "sku": "SKU-NEW",
          "name": "   ",
          "price": -1.00,
          "inventoryStatus": "IN_STOCK"
        }
        """;
  }

  private static String invalidUpdateJson() {
    return """
        {
          "name": "   ",
          "price": 13.123,
          "inventoryStatus": "LOW_STOCK"
        }
        """;
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

  private static CommercePrincipal principal() {
    return new CommercePrincipal("merchant", Set.of("catalog:write"), "fingerprint-merchant");
  }

  private static CommercePrincipal nonWriterPrincipal() {
    return new CommercePrincipal("shopper", Set.of("catalog:read"), "fingerprint-shopper");
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
    return new RecordingProductRepository(List.of(
        new Product(
            new ProductId(MUG_ID),
            new Sku("MUG-001"),
            new ProductName("Starter Mug"),
            Money.usd("12.50"),
            InventoryStatus.IN_STOCK,
            new StoreId("main")),
        new Product(
            new ProductId(BAG_ID),
            new Sku("BAG-002"),
            new ProductName("Travel Bag"),
            Money.usd("48.00"),
            InventoryStatus.LOW_STOCK,
            new StoreId("main"))));
  }

  private static final class RecordingProductRepository implements ProductRepository {

    private final Map<ProductId, Product> products = new LinkedHashMap<>();
    private int saveCount;

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
      saveCount++;
      products.put(product.id(), product);
      return product;
    }

    int saveCount() {
      return saveCount;
    }
  }
}
