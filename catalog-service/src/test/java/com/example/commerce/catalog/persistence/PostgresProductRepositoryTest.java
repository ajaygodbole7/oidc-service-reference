package com.example.commerce.catalog.persistence;

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
import com.example.commerce.web.error.ConflictException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the Postgres-backed catalog repository against a real Postgres (Flyway migration +
 * seed, read, assigned-id insert-then-update, and the keyset page query). Loading the full Spring
 * context here also guards catalog's bean wiring the way OrderServiceContextTest guards order-service.
 */
@Testcontainers
@SpringBootTest
// "test" is in the SecretSentinelGuard local-profile allow-list, so the committed dev-default
// secrets downgrade to a WARN instead of failing the context boot.
@ActiveProfiles("test")
class PostgresProductRepositoryTest {

  // Canonical seed TSIDs from V1__create_products.sql, in id (= keyset) order.
  private static final String MUG_ID = "6801HWW000000";
  private static final String BAG_ID = "6801HWW00YGJ3";
  private static final String LAMP_ID = "6801HWW01X146";
  private static final String NOTEBOOK_ID = "6801HWW02VHP9";
  private static final String TOTE_ID = "6801HWW03T28C";

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.4");

  @Autowired
  private ProductRepository repository;

  @Test
  void readsFlywaySeededProducts() {
    assertThat(repository.findById(new ProductId(MUG_ID)))
        .get()
        .extracting(product -> product.sku().value())
        .isEqualTo("MUG-001");
    assertThat(repository.findAll())
        .extracting(product -> product.id().value())
        .contains(MUG_ID, BAG_ID, LAMP_ID, NOTEBOOK_ID, TOTE_ID);
  }

  @Test
  void findByIdReturnsEmptyOptionalForAnUnknownId() {
    assertThat(repository.findById(new ProductId("6801HWWMISSING"))).isEmpty();
  }

  @Test
  void insertWithDuplicateSkuThrowsConflict() {
    Product first = new Product(
        new ProductId("6801HWWDUP001"),
        new Sku("DUP-SKU-1"),
        new ProductName("First"),
        new Money(new BigDecimal("5.00"), "USD"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"));
    repository.save(first);

    // A different id but the SAME sku violates the products_sku_key unique constraint. The
    // repository must translate Postgres's DuplicateKeyException into a 409 ConflictException
    // (RFC 9457 via the shared GlobalExceptionHandler), not let it surface as a 500.
    Product duplicate = new Product(
        new ProductId("6801HWWDUP002"),
        new Sku("DUP-SKU-1"),
        new ProductName("Second"),
        new Money(new BigDecimal("9.00"), "USD"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"));

    assertThatThrownBy(() -> repository.save(duplicate))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("DUP-SKU-1");
  }

  @Test
  void findPageOrdersByTsidAndHonorsLimit() {
    // afterId null => first page from the start, ordered by id (TSID = insertion order).
    List<Product> firstTwo = repository.findPage(null, 2);

    assertThat(firstTwo).extracting(product -> product.id().value())
        .containsExactly(MUG_ID, BAG_ID);
  }

  @Test
  void findPageKeysetWalksTheSeedInIdOrder() {
    // Walk the seed page by page using the last id as the cursor; over-fetch limit + 1 each time.
    List<Product> page1 = repository.findPage(null, 3);
    assertThat(page1).extracting(product -> product.id().value())
        .containsExactly(MUG_ID, BAG_ID, LAMP_ID);

    String afterId = page1.get(page1.size() - 1).id().value();
    List<Product> page2 = repository.findPage(afterId, 3);
    // The seed's next two ids in keyset order; other tests may append ids after TOTE_ID, so assert
    // the leading sequence rather than strict equality.
    assertThat(page2).extracting(product -> product.id().value())
        .startsWith(NOTEBOOK_ID, TOTE_ID);
  }

  @Test
  void findPageOverFetchOneDetectsWhetherMorePagesRemain() {
    // pageSize = 2, over-fetch limit = 3: the seed has > 2 rows, so a third row comes back and the
    // caller knows another page exists. (Asserts the over-fetch contract, not a fixed row count, so
    // it is robust to rows other tests may insert beyond the seed range.)
    assertThat(repository.findPage(null, 3)).hasSize(3);

    // From the last seeded id, the keyset window holds no SEED rows (no more seed pages). Other
    // tests may insert ids that sort after TOTE_ID, so assert none of the seed ids reappear rather
    // than asserting strict emptiness.
    assertThat(repository.findPage(TOTE_ID, 10))
        .extracting(product -> product.id().value())
        .doesNotContain(MUG_ID, BAG_ID, LAMP_ID, NOTEBOOK_ID, TOTE_ID);
  }

  @Test
  void insertsThenUpdatesByAssignedId() {
    Product created = new Product(
        new ProductId("6801HWW0ZZZZZ"),
        new Sku("WID-9"),
        new ProductName("Widget"),
        new Money(new BigDecimal("5.00"), "USD"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"));
    repository.save(created);
    assertThat(repository.findById(new ProductId("6801HWW0ZZZZZ"))).isPresent();

    repository.save(created.update(
        new ProductName("Widget v2"), new Money(new BigDecimal("6.00"), "USD"), InventoryStatus.LOW_STOCK));

    Product reloaded = repository.findById(new ProductId("6801HWW0ZZZZZ")).orElseThrow();
    assertThat(reloaded.name().value()).isEqualTo("Widget v2");
    assertThat(reloaded.price().amount()).isEqualByComparingTo("6.00");
    assertThat(reloaded.inventoryStatus()).isEqualTo(InventoryStatus.LOW_STOCK);
  }
}
