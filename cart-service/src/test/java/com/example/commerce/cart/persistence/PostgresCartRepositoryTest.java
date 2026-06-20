package com.example.commerce.cart.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.domain.Quantity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the Postgres-backed cart repository against real Postgres, including the cart_items
 * child collection (read seed, persist items, and replace items on re-save).
 */
@Testcontainers
@SpringBootTest
@Transactional
class PostgresCartRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private CartRepository repository;

  @Test
  void readsFlywaySeededCartByOwnerAndId() {
    assertThat(repository.findByOwnerSub("alice"))
        .get()
        .extracting(cart -> cart.id().value())
        .isEqualTo("alice-cart");
    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).isEmpty();
  }

  @Test
  void persistsItemsThenReplacesThemOnReSave() {
    Cart cart = new Cart(new CartId("alice-cart"), "alice", List.of());
    cart.addItem(new ProductId("starter-mug"), new Quantity(2), new Money(new BigDecimal("12.50"), "USD"));
    repository.save(cart);

    Cart reloaded = repository.findById(new CartId("alice-cart")).orElseThrow();
    assertThat(reloaded.items()).hasSize(1);
    assertThat(reloaded.total().amount()).isEqualByComparingTo("25.00");

    reloaded.addItem(new ProductId("travel-bag"), new Quantity(1), new Money(new BigDecimal("48.00"), "USD"));
    repository.save(reloaded);

    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).hasSize(2);
  }
}
