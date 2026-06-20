package com.example.commerce.catalog.service;

import com.example.commerce.catalog.domain.InventoryStatus;
import com.example.commerce.catalog.domain.Money;
import com.example.commerce.catalog.domain.ProductId;
import com.example.commerce.catalog.domain.ProductName;
import com.example.commerce.catalog.domain.Sku;
import com.example.commerce.catalog.domain.StoreId;

public record CreateProductCommand(
    ProductId productId,
    Sku sku,
    ProductName name,
    Money price,
    InventoryStatus inventoryStatus,
    StoreId storeId) {
}
