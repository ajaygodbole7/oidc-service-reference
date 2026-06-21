package com.example.commerce.catalog.service;

import com.example.commerce.catalog.domain.Product;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductRepository;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.Permission;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.web.pagination.CursorPaginator;
import com.example.commerce.web.pagination.Page;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class CatalogApplicationService {

  private static final String CATALOG_WRITE = "catalog:write";
  private static final Permission MANAGE = new Permission("manage");
  private static final ResourceRef MAIN_STORE = new ResourceRef("store", "main");

  private final ProductRepository repository;
  private final ScopeAuthorizer scopeAuthorizer;
  private final ResourceAuthorizer resourceAuthorizer;
  private final CursorPaginator paginator;

  public CatalogApplicationService(
      ProductRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      CursorPaginator paginator) {
    this.repository = repository;
    this.scopeAuthorizer = scopeAuthorizer;
    this.resourceAuthorizer = resourceAuthorizer;
    this.paginator = paginator;
  }

  /**
   * One keyset page of products ordered by id (TSID, so insertion-time-sortable). {@code limit} is
   * clamped by {@link CursorPaginator#resolveLimit} (null/&lt;1 -&gt; default, &gt;max -&gt; capped); a null or
   * malformed cursor starts at the first page. The repository over-fetches {@code limit + 1} rows so
   * the paginator can split off {@code nextCursor}.
   */
  public Page<Product> listProducts(@Nullable Integer limit, @Nullable String cursor) {
    int pageSize = paginator.resolveLimit(limit);
    String afterId = CursorPaginator.decodeCursor(cursor);
    List<Product> fetched = repository.findPage(afterId, pageSize + 1);
    return CursorPaginator.paginate(fetched, pageSize, product -> product.id().value());
  }

  public Product getProduct(ProductId productId) {
    return repository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException(productId));
  }

  public CatalogResult createProduct(CommercePrincipal principal, CreateProductCommand command) {
    List<DecisionTrace> traces = requireMerchantManage(principal);
    Product product = new Product(
        command.productId(),
        command.sku(),
        command.name(),
        command.price(),
        command.inventoryStatus(),
        command.storeId());
    repository.save(product);
    return new CatalogResult(product, traces);
  }

  public CatalogResult updateProduct(
      CommercePrincipal principal, ProductId productId, UpdateProductCommand command) {
    Product current = getProduct(productId);
    List<DecisionTrace> traces = requireMerchantManage(principal);
    Product updated = current.update(command.name(), command.price(), command.inventoryStatus());
    repository.save(updated);
    return new CatalogResult(updated, traces);
  }

  private List<DecisionTrace> requireMerchantManage(CommercePrincipal principal) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, CATALOG_WRITE);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, MAIN_STORE, MANAGE);
    return List.of(scopeTrace, resourceTrace);
  }
}
