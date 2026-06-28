package com.example.commerce.cart.web;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartItem;
import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.service.CartResult;
import java.util.List;

record CartResponse(
    String id,
    String currency,
    List<Item> items,
    long subtotalCents,
    long estimatedTaxCents,
    long totalCents) {

  static CartResponse from(CartResult result) {
    Cart cart = result.cart();
    Money total = cart.total();
    return new CartResponse(
        cart.id().value(),
        total.currency(),
        cart.items().stream().map(Item::from).toList(),
        cents(total),
        // TODO: Calculate tax in the checkout/order slice; cart subtotal is the current total.
        0,
        cents(total));
  }

  record Item(
      String id,
      String productId,
      String name,
      int quantity,
      long unitPriceCents,
      long lineTotalCents) {

    static Item from(CartItem item) {
      String productId = item.productId().value();
      return new Item(
          productId,
          productId,
          // TODO: Replace this id echo with the catalog product name when catalog-service is wired.
          productId,
          item.quantity().value(),
          cents(item.unitPrice()),
          cents(item.lineTotal()));
    }
  }

  private static long cents(Money money) {
    return money.amount().movePointRight(2).longValueExact();
  }
}
