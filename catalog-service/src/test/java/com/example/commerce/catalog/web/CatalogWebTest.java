package com.example.commerce.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ScopeAuthorizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CatalogWebTest {

  private final RecordingProductRepository repository = repository();
  private final CatalogApplicationService service = new CatalogApplicationService(
      repository,
      new ScopeAuthorizer(),
      new ResourceAuthorizer((subject, resource, permission) -> AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"))));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new CatalogController(service))
      .setControllerAdvice(new RestExceptionHandler())
      .setValidator(validator())
      .build();

  @Test
  void anonymous_list_read_returns_public_product_projection() throws Exception {
    mockMvc.perform(get("/api/catalog/products"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.products[0].id").value("starter-mug"))
        .andExpect(jsonPath("$.products[0].storeId").doesNotExist());
  }

  @Test
  void anonymous_detail_read_returns_public_product_projection() throws Exception {
    mockMvc.perform(get("/api/catalog/products/starter-mug"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("starter-mug"))
        .andExpect(jsonPath("$.storeId").doesNotExist());
  }

  @Test
  void missing_principal_on_write_returns_problem_json_401() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void create_product_with_principal_returns_created_public_projection() throws Exception {
    mockMvc.perform(post("/api/catalog/products")
            .requestAttr("commercePrincipal", principal())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson()))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("new-product"))
        .andExpect(jsonPath("$.storeId").doesNotExist());
  }

  @Test
  void patch_product_with_principal_updates_public_projection() throws Exception {
    mockMvc.perform(patch("/api/catalog/products/starter-mug")
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
        .andExpect(jsonPath("$.name").value("Updated Mug"));
  }

  private static String createJson() {
    return """
        {
          "id": "new-product",
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

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  private static RecordingProductRepository repository() {
    return new RecordingProductRepository(List.of(new Product(
        new ProductId("starter-mug"),
        new Sku("MUG-001"),
        new ProductName("Starter Mug"),
        Money.usd("12.50"),
        InventoryStatus.IN_STOCK,
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
