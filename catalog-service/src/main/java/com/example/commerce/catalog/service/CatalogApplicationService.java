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
import java.util.List;

public final class CatalogApplicationService {

  private static final String CATALOG_WRITE = "catalog:write";
  private static final Permission MANAGE = new Permission("manage");
  private static final ResourceRef MAIN_STORE = new ResourceRef("store", "main");

  private final ProductRepository repository;
  private final ScopeAuthorizer scopeAuthorizer;
  private final ResourceAuthorizer resourceAuthorizer;

  public CatalogApplicationService(
      ProductRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    this.repository = repository;
    this.scopeAuthorizer = scopeAuthorizer;
    this.resourceAuthorizer = resourceAuthorizer;
  }

  public List<Product> listProducts() {
    return repository.findAll();
  }

  public Product getProduct(ProductId productId) {
    return repository.findById(productId)
        .orElseThrow(() -> new ProductNotFoundException("product not found: " + productId.value()));
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
