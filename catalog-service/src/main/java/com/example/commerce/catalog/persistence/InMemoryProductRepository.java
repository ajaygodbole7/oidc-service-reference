package com.example.commerce.catalog.persistence;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProductRepository implements ProductRepository {

  private final Map<ProductId, Product> products = new ConcurrentHashMap<>();

  public InMemoryProductRepository(List<Product> initialProducts) {
    initialProducts.forEach(this::save);
  }

  public static InMemoryProductRepository withLocalFixtures() {
    return new InMemoryProductRepository(List.of(
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

  @Override
  public List<Product> findAll() {
    return products.values().stream()
        .sorted(Comparator.comparing(product -> product.id().value()))
        .toList();
  }

  @Override
  public Optional<Product> findById(ProductId productId) {
    return Optional.ofNullable(products.get(productId));
  }

  @Override
  public Product save(Product product) {
    products.put(product.id(), product);
    return product;
  }
}
