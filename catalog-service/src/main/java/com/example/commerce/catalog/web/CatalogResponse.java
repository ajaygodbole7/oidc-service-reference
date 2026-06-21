package com.example.commerce.catalog.web;

import com.example.commerce.catalog.domain.Product;
import com.example.commerce.web.pagination.Page;
import java.util.List;
import org.jspecify.annotations.Nullable;

record ProductListResponse(List<ProductResponse> products, @Nullable String nextCursor) {

  static ProductListResponse from(Page<Product> page) {
    return new ProductListResponse(
        page.items().stream().map(ProductResponse::from).toList(), page.nextCursor());
  }
}

record ProductResponse(
    String id,
    String sku,
    String name,
    String currency,
    long priceCents,
    String inventoryStatus) {

  static ProductResponse from(Product product) {
    return new ProductResponse(
        product.id().value(),
        product.sku().value(),
        product.name().value(),
        product.price().currency(),
        product.price().amount().movePointRight(2).longValueExact(),
        product.inventoryStatus().name());
  }
}
