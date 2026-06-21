/**
 * Generic keyset (cursor) pagination helpers: opaque cursor encode/decode over the last TSID and a
 * page-size cap. Service repositories fetch {@code limit + 1} rows and call
 * {@link com.example.commerce.web.pagination.CursorPaginator#paginate} to split off the next cursor.
 */
@org.jspecify.annotations.NullMarked
package com.example.commerce.web.pagination;
