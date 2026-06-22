package com.example.commerce.catalog.persistence;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;
import com.example.commerce.web.error.ConflictException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
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
  public List<Product> findPage(@Nullable String afterId, int limit) {
    List<Product> products = new ArrayList<>();
    rows.findPage(afterId, limit).forEach(row -> products.add(toDomain(row)));
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
      try {
        aggregateTemplate.insert(row);
      } catch (DuplicateKeyException exception) {
        if (isSkuConflict(exception)) {
          throw new ConflictException(
              "duplicate-sku",
              "A product with SKU '" + product.sku().value() + "' already exists.",
              exception);
        }
        throw exception;
      }
    }
    return product;
  }

  // Translate the products_sku_key unique-constraint violation into a 409 ConflictException
  // (RFC 9457 via the shared GlobalExceptionHandler) instead of letting Postgres's
  // DuplicateKeyException surface as a 500. Mirrors order-service's
  // PostgresIdempotencyRepository.isIdempotencyKeyConflict.
  private static boolean isSkuConflict(DuplicateKeyException exception) {
    String message = String.valueOf(exception.getMostSpecificCause().getMessage());
    return message.contains("products_sku_key");
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
