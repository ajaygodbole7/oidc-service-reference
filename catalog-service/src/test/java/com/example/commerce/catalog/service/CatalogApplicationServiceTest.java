package com.example.commerce.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.AuthorizationDeniedException;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InMemoryAuthorizationClient;
import com.example.commerce.security.Permission;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogApplicationServiceTest {

  private static final ResourceRef MAIN_STORE = new ResourceRef("store", "main");
  private static final Permission MANAGE = new Permission("manage");

  @Test
  void lists_products_without_principal_or_authorization_checks() {
    RecordingAuthorizationClient authorizationClient = recordingClient(managerGrant());
    CatalogApplicationService service = service(repository(), authorizationClient);

    List<Product> products = service.listProducts();

    assertThat(products).extracting(product -> product.id().value())
        .containsExactly("starter-mug", "travel-bag");
    assertThat(authorizationClient.requests()).isEmpty();
  }

  @Test
  void gets_product_detail_without_principal_or_authorization_checks() {
    RecordingAuthorizationClient authorizationClient = recordingClient(managerGrant());
    CatalogApplicationService service = service(repository(), authorizationClient);

    Product product = service.getProduct(new ProductId("starter-mug"));

    assertThat(product.name().value()).isEqualTo("Starter Mug");
    assertThat(authorizationClient.requests()).isEmpty();
  }

  @Test
  void create_product_requires_catalog_write_scope_before_resource_check() {
    RecordingProductRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = recordingClient(managerGrant());
    CatalogApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.createProduct(principal("merchant"), createCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope catalog:write");

    assertThat(authorizationClient.requests()).isEmpty();
    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void create_product_requires_store_manage_relationship_before_save() {
    RecordingProductRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CatalogApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.createProduct(principal("merchant", "catalog:write"), createCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");

    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:merchant", "store:main", "manage"));
    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void create_product_saves_after_scope_and_store_manage_pass() {
    RecordingProductRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = recordingClient(managerGrant());
    CatalogApplicationService service = service(repository, authorizationClient);

    CatalogResult result = service.createProduct(principal("merchant", "catalog:write"), createCommand());

    assertThat(result.product().id()).isEqualTo(new ProductId("new-sku"));
    assertThat(result.traces()).extracting(DecisionTrace::gate).containsExactly("scope", "resource");
    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:merchant", "store:main", "manage"));
    assertThat(repository.saveCount()).isEqualTo(1);
    assertThat(repository.findById(new ProductId("new-sku"))).isPresent();
  }

  @Test
  void update_product_requires_store_manage_relationship_before_save() {
    RecordingProductRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CatalogApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.updateProduct(
            principal("merchant", "catalog:write"),
            new ProductId("starter-mug"),
            updateCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");

    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:merchant", "store:main", "manage"));
    assertThat(repository.saveCount()).isZero();
  }

  private static CatalogApplicationService service(
      ProductRepository repository, AuthorizationClient authorizationClient) {
    return new CatalogApplicationService(
        repository,
        new ScopeAuthorizer(),
        new ResourceAuthorizer(authorizationClient));
  }

  private static RecordingProductRepository repository() {
    return new RecordingProductRepository(List.of(
        new Product(
            new ProductId("starter-mug"),
            new Sku("MUG-001"),
            new ProductName("Starter Mug"),
            Money.usd("12.50"),
            InventoryStatus.IN_STOCK,
            new StoreId("main")),
        new Product(
            new ProductId("travel-bag"),
            new Sku("BAG-002"),
            new ProductName("Travel Bag"),
            Money.usd("48.00"),
            InventoryStatus.LOW_STOCK,
            new StoreId("main"))));
  }

  private static CreateProductCommand createCommand() {
    return new CreateProductCommand(
        new ProductId("new-sku"),
        new Sku("SKU-NEW"),
        new ProductName("New Product"),
        Money.usd("19.99"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"));
  }

  private static UpdateProductCommand updateCommand() {
    return new UpdateProductCommand(
        new ProductName("Updated Mug"),
        Money.usd("13.00"),
        InventoryStatus.IN_STOCK);
  }

  private static CommercePrincipal principal(String subject, String... scopes) {
    return new CommercePrincipal(subject, Set.of(scopes), "fingerprint-" + subject);
  }

  private static InMemoryAuthorizationClient managerGrant() {
    return new InMemoryAuthorizationClient().grant(SubjectRef.user("merchant"), MAIN_STORE, MANAGE);
  }

  private static RecordingAuthorizationClient recordingClient(AuthorizationClient delegate) {
    return new RecordingAuthorizationClient(delegate);
  }

  private record CheckRequest(String subject, String resource, String permission) {
  }

  private static final class RecordingAuthorizationClient implements AuthorizationClient {

    private final AuthorizationClient delegate;
    private final List<CheckRequest> requests = new ArrayList<>();

    private RecordingAuthorizationClient(AuthorizationClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission) {
      requests.add(new CheckRequest(subject.toString(), resource.toString(), permission.value()));
      return delegate.check(subject, resource, permission);
    }

    @Override
    public void writeRelationship(Relationship relationship) {
      delegate.writeRelationship(relationship);
    }

    @Override
    public void deleteRelationship(Relationship relationship) {
      delegate.deleteRelationship(relationship);
    }

    List<CheckRequest> requests() {
      return List.copyOf(requests);
    }
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
