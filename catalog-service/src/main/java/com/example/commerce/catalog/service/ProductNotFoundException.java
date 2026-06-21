package com.example.commerce.catalog.service;

import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.web.error.ResourceNotFoundException;

/**
 * Thrown when no product matches the requested id. Extends the shared {@link ResourceNotFoundException}
 * base so the starter's single {@code GlobalExceptionHandler} maps it to a 404 RFC 9457 problem with
 * {@code type=<base-url>/product-not-found} and {@code errorCode=PRODUCT_NOT_FOUND}.
 */
public final class ProductNotFoundException extends ResourceNotFoundException {

  public ProductNotFoundException(ProductId id) {
    super("product-not-found", "no product with id " + id.value());
  }
}
