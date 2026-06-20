package com.example.commerce.catalog.domain;

public record Product(
    ProductId id,
    Sku sku,
    ProductName name,
    Money price,
    InventoryStatus inventoryStatus,
    StoreId storeId) {

  public Product {
    if (id == null) {
      throw new IllegalArgumentException("product id is required");
    }
    if (sku == null) {
      throw new IllegalArgumentException("sku is required");
    }
    if (name == null) {
      throw new IllegalArgumentException("product name is required");
    }
    if (price == null) {
      throw new IllegalArgumentException("price is required");
    }
    if (inventoryStatus == null) {
      throw new IllegalArgumentException("inventory status is required");
    }
    if (storeId == null) {
      throw new IllegalArgumentException("store id is required");
    }
  }

  public Product update(ProductName nextName, Money nextPrice, InventoryStatus nextInventoryStatus) {
    return new Product(id, sku, nextName, nextPrice, nextInventoryStatus, storeId);
  }
}
