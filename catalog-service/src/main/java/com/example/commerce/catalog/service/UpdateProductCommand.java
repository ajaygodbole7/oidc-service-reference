package com.example.commerce.catalog.service;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.ProductName;

public record UpdateProductCommand(
    ProductName name,
    Money price,
    InventoryStatus inventoryStatus) {
}
