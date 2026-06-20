package com.example.commerce.catalog.persistence;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row for the {@code products} table. Kept separate from the domain
 * {@code Product} so the domain stays free of persistence annotations.
 */
@Table("products")
record ProductRow(
    @Id String id,
    String sku,
    String name,
    @Column("price_amount") BigDecimal priceAmount,
    @Column("price_currency") String priceCurrency,
    @Column("inventory_status") String inventoryStatus,
    @Column("store_id") String storeId) {
}
