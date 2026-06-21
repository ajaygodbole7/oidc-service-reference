package com.example.commerce.cart.persistence;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for {@code carts}. Its items are a child collection in
 * {@code cart_items} (back-referenced by {@code cart_id}); Spring Data JDBC cascades them on save.
 */
@Table("carts")
record CartRow(
    @Id String id,
    @Column("owner_sub") String ownerSub,
    @MappedCollection(idColumn = "cart_id", keyColumn = "item_position") List<CartItemRow> items) {
}
