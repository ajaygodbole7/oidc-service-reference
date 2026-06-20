package com.example.commerce.catalog.web;

import com.example.commerce.catalog.domain.Product;
import java.util.List;

record ProductListResponse(List<ProductResponse> products) {

  static ProductListResponse from(List<Product> products) {
    return new ProductListResponse(products.stream().map(ProductResponse::from).toList());
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
