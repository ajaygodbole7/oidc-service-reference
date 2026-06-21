package com.example.commerce.catalog.web;

import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.StoreId;
import com.example.commerce.catalog.service.CatalogApplicationService;
import com.example.commerce.catalog.service.CreateProductCommand;
import com.example.commerce.catalog.service.UpdateProductCommand;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.web.tsid.TsidGenerator;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController {

  private final CatalogApplicationService service;
  private final TsidGenerator tsidGenerator;

  CatalogController(CatalogApplicationService service, TsidGenerator tsidGenerator) {
    this.service = service;
    this.tsidGenerator = tsidGenerator;
  }

  @GetMapping("/api/catalog/products")
  ProductListResponse products(
      @RequestParam(name = "limit", required = false) @Nullable Integer limit,
      @RequestParam(name = "cursor", required = false) @Nullable String cursor) {
    return ProductListResponse.from(service.listProducts(limit, cursor));
  }

  @GetMapping("/api/catalog/products/{productId}")
  ProductResponse product(@PathVariable String productId) {
    return ProductResponse.from(service.getProduct(new ProductId(productId)));
  }

  @PostMapping("/api/catalog/products")
  @ResponseStatus(HttpStatus.CREATED)
  ProductResponse createProduct(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @Valid @RequestBody CatalogRequest.CreateProduct request) {
    CreateProductCommand command = new CreateProductCommand(
        new ProductId(tsidGenerator.newId()),
        request.skuValue(),
        request.nameValue(),
        request.priceValue(),
        request.inventoryStatusValue(),
        new StoreId("main"));
    return ProductResponse.from(service.createProduct(principal, command).product());
  }

  @PutMapping("/api/catalog/products/{productId}")
  ProductResponse updateProduct(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @PathVariable String productId,
      @Valid @RequestBody CatalogRequest.UpdateProduct request) {
    UpdateProductCommand command = new UpdateProductCommand(
        request.nameValue(),
        request.priceValue(),
        request.inventoryStatusValue());
    return ProductResponse.from(
        service.updateProduct(principal, new ProductId(productId), command).product());
  }
}
