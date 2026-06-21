package com.example.commerce.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code orders}. Lines are child rows replaced on save.
 */
@Table("orders")
record OrderRow(
    @Id String id,
    @Column("owner_sub") String ownerSub,
    @Column("source_cart_id") String sourceCartId,
    @Column("total_amount") BigDecimal totalAmount,
    @Column("total_currency") String totalCurrency,
    @Column("payment_authorization_id") String paymentAuthorizationId,
    @Column("created_at") Instant createdAt,
    String status,
    @MappedCollection(idColumn = "order_id", keyColumn = "line_position") List<OrderLineRow> lines) {
}
