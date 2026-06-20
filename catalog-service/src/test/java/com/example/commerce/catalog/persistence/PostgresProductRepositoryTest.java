package com.example.commerce.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the Postgres-backed catalog repository against a real Postgres (Flyway migration +
 * seed, read, and assigned-id insert-then-update). Loading the full Spring context here also
 * guards catalog's bean wiring the way OrderServiceContextTest guards order-service.
 */
@Testcontainers
@SpringBootTest
class PostgresProductRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.4");

  @Autowired
  private ProductRepository repository;

  @Test
  void readsFlywaySeededProducts() {
    assertThat(repository.findById(new ProductId("starter-mug")))
        .get()
        .extracting(product -> product.sku().value())
        .isEqualTo("MUG-001");
    assertThat(repository.findAll())
        .extracting(product -> product.id().value())
        .contains("starter-mug", "travel-bag");
  }

  @Test
  void insertsThenUpdatesByAssignedId() {
    Product created = new Product(
        new ProductId("widget-1"),
        new Sku("WID-9"),
        new ProductName("Widget"),
        new Money(new BigDecimal("5.00"), "USD"),
        InventoryStatus.IN_STOCK,
        new StoreId("main"));
    repository.save(created);
    assertThat(repository.findById(new ProductId("widget-1"))).isPresent();

    repository.save(created.update(
        new ProductName("Widget v2"), new Money(new BigDecimal("6.00"), "USD"), InventoryStatus.LOW_STOCK));

    Product reloaded = repository.findById(new ProductId("widget-1")).orElseThrow();
    assertThat(reloaded.name().value()).isEqualTo("Widget v2");
    assertThat(reloaded.price().amount()).isEqualByComparingTo("6.00");
    assertThat(reloaded.inventoryStatus()).isEqualTo(InventoryStatus.LOW_STOCK);
  }
}
