package com.example.commerce.catalog.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

  List<Product> findAll();

  Optional<Product> findById(ProductId productId);

  Product save(Product product);
}
