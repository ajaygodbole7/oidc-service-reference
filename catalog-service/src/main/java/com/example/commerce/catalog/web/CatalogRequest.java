package com.example.commerce.catalog.web;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.Sku;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class CatalogRequest {

  private CatalogRequest() {
  }

  public record CreateProduct(
      @NotBlank String sku,
      @NotBlank String name,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
      @NotNull InventoryStatus inventoryStatus) {

    Sku skuValue() {
      return new Sku(sku);
    }

    ProductName nameValue() {
      return new ProductName(name);
    }

    Money priceValue() {
      return new Money(price, "USD");
    }

    InventoryStatus inventoryStatusValue() {
      return inventoryStatus;
    }
  }

  public record UpdateProduct(
      @NotBlank String name,
      @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
      @NotNull InventoryStatus inventoryStatus) {

    ProductName nameValue() {
      return new ProductName(name);
    }

    Money priceValue() {
      return new Money(price, "USD");
    }

    InventoryStatus inventoryStatusValue() {
      return inventoryStatus;
    }
  }
}
