package com.example.commerce.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CartDomainTest {

  @Test
  void quantity_must_be_positive() {
    assertThatThrownBy(() -> new Quantity(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }

  @Test
  void removing_missing_item_denies_the_mutation() {
    Cart cart = new Cart(new CartId("alice-cart"), "alice", List.of());

    assertThatThrownBy(() -> cart.removeItem(new ProductId("sku-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void total_is_computed_from_items() {
    Cart cart = new Cart(new CartId("alice-cart"), "alice", List.of());

    cart.addItem(new ProductId("sku-1"), new Quantity(2), Money.usd("12.50"));
    cart.addItem(new ProductId("sku-2"), new Quantity(1), Money.usd("5.00"));

    assertThat(cart.total()).isEqualTo(Money.usd("30.00"));
  }

  @Test
  void total_uses_the_item_currency_instead_of_a_usd_identity() {
    Cart cart = new Cart(new CartId("alice-cart"), "alice", List.of());

    cart.addItem(new ProductId("sku-1"), new Quantity(2), new Money(new java.math.BigDecimal("12.50"), "eur"));

    assertThat(cart.total()).isEqualTo(new Money(new java.math.BigDecimal("25.00"), "EUR"));
  }

  @Test
  void money_rejects_more_than_two_decimal_places_as_invalid_input() {
    assertThatThrownBy(() -> Money.usd("10.123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most two decimal places");
  }
}
