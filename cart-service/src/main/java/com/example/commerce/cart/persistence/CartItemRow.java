package com.example.commerce.cart.persistence;

import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Child row of {@link CartRow} in {@code cart_items}; the {@code cart_id} back-reference is managed by Spring Data JDBC. */
@Table("cart_items")
record CartItemRow(
    @Column("product_id") String productId,
    int quantity,
    @Column("unit_price_amount") BigDecimal unitPriceAmount,
    @Column("unit_price_currency") String unitPriceCurrency) {
}
