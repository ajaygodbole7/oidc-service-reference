package com.example.commerce.catalog.persistence;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Postgres-backed {@link ProductRepository}. The product id is a business-assigned key, so
 * {@code save} chooses insert vs update explicitly (Spring Data JDBC's CrudRepository.save would
 * always issue an UPDATE for a non-null assigned id).
 */
public final class PostgresProductRepository implements ProductRepository {

  private final CatalogProductRowRepository rows;
  private final JdbcAggregateTemplate aggregateTemplate;

  public PostgresProductRepository(
      CatalogProductRowRepository rows, JdbcAggregateTemplate aggregateTemplate) {
    this.rows = rows;
    this.aggregateTemplate = aggregateTemplate;
  }

  @Override
  public List<Product> findAll() {
    List<Product> products = new ArrayList<>();
    rows.findAll().forEach(row -> products.add(toDomain(row)));
    products.sort(Comparator.comparing(product -> product.id().value()));
    return products;
  }

  @Override
  public Optional<Product> findById(ProductId productId) {
    return rows.findById(productId.value()).map(PostgresProductRepository::toDomain);
  }

  @Override
  public Product save(Product product) {
    ProductRow row = toRow(product);
    if (rows.existsById(product.id().value())) {
      aggregateTemplate.update(row);
    } else {
      aggregateTemplate.insert(row);
    }
    return product;
  }

  private static ProductRow toRow(Product product) {
    return new ProductRow(
        product.id().value(),
        product.sku().value(),
        product.name().value(),
        product.price().amount(),
        product.price().currency(),
        product.inventoryStatus().name(),
        product.storeId().value());
  }

  private static Product toDomain(ProductRow row) {
    return new Product(
        new ProductId(row.id()),
        new Sku(row.sku()),
        new ProductName(row.name()),
        new Money(row.priceAmount(), row.priceCurrency()),
        InventoryStatus.valueOf(row.inventoryStatus()),
        new StoreId(row.storeId()));
  }
}
