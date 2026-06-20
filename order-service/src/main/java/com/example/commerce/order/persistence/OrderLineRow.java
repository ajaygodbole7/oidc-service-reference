package com.example.commerce.order.persistence;

import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Child row of {@link OrderRow} in {@code order_lines}; Spring Data JDBC manages {@code order_id}. */
@Table("order_lines")
record OrderLineRow(
    @Column("product_id") String productId,
    int quantity,
    @Column("unit_price_amount") BigDecimal unitPriceAmount,
    @Column("unit_price_currency") String unitPriceCurrency) {
}
