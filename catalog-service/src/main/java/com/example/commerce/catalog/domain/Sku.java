package com.example.commerce.catalog.domain;

public record Sku(String value) {

  public Sku {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("sku is required");
    }
  }
}
