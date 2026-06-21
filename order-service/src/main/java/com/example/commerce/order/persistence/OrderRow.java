package com.example.commerce.order.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code orders}. Lines are child rows replaced on save.
 *
 * <p>{@code version} is the optimistic-locking column ({@link Version}): {@code null} marks a
 * not-yet-persisted aggregate (drives INSERT), and every UPDATE asserts the stored version and bumps
 * it. {@link PostgresOrderRepository} reloads the current version before a re-persist so the
 * recover-forward path still converges while a genuinely concurrent mutation fails the lock.
 */
@Table("orders")
record OrderRow(
    @Id String id,
    @Version @Nullable Long version,
    @Column("owner_sub") String ownerSub,
    @Column("source_cart_id") String sourceCartId,
    @Column("total_amount") BigDecimal totalAmount,
    @Column("total_currency") String totalCurrency,
    @Column("payment_authorization_id") String paymentAuthorizationId,
    @Column("created_at") Instant createdAt,
    String status,
    @MappedCollection(idColumn = "order_id", keyColumn = "line_position") List<OrderLineRow> lines) {
}
