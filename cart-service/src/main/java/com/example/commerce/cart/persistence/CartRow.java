package com.example.commerce.cart.persistence;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code carts}. Its items are a child collection in
 * {@code cart_items} (back-referenced by {@code cart_id}); Spring Data JDBC cascades them on save.
 *
 * <p>The id is application-assigned (a TSID), so it cannot drive insert-vs-update on its own — a
 * manually-set {@code @Id} always looks "existing" to Spring Data JDBC. The {@code @Version} field
 * does the dispatch instead: {@code version == null} is an INSERT (and is stamped to 0), a non-null
 * version is an UPDATE emitted as {@code WHERE version = ?} that bumps the version. That predicate
 * is the lost-update guard; a stale version updates zero rows and Spring Data raises
 * {@code OptimisticLockingFailureException}.
 */
@Table("carts")
record CartRow(
    @Id String id,
    @Column("owner_sub") String ownerSub,
    @Version @Nullable Long version,
    @MappedCollection(idColumn = "cart_id", keyColumn = "item_position") List<CartItemRow> items) {
}
