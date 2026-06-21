package com.example.commerce.web.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Keyset pagination over fixed-width sortable TSID ids. Usage in a service:
 *
 * <pre>{@code
 * int limit = paginator.resolveLimit(requestedSize);          // clamp to [1, maxPageSize]
 * String afterId = paginator.decodeCursor(requestCursor);     // null => first page
 * List<Row> rows = repo.findKeyset(afterId, limit + 1);       // OVER-fetch by one
 * Page<Row> page = paginator.paginate(rows, limit, Row::id);  // splits off nextCursor
 * }</pre>
 *
 * The cursor is the opaque base64url of the last returned row's id; it carries no offset and no
 * server state. A blank or malformed cursor decodes to null (first page) — never an error.
 */
public final class CursorPaginator {

  private final int defaultPageSize;
  private final int maxPageSize;

  public CursorPaginator(int defaultPageSize, int maxPageSize) {
    if (defaultPageSize < 1) {
      throw new IllegalArgumentException("defaultPageSize must be >= 1");
    }
    if (maxPageSize < defaultPageSize) {
      throw new IllegalArgumentException("maxPageSize must be >= defaultPageSize");
    }
    this.defaultPageSize = defaultPageSize;
    this.maxPageSize = maxPageSize;
  }

  public int defaultPageSize() {
    return defaultPageSize;
  }

  public int maxPageSize() {
    return maxPageSize;
  }

  /**
   * Clamp a requested page size into {@code [1, maxPageSize]}. A null or non-positive request falls
   * back to {@code defaultPageSize}; an over-large request is clamped down to {@code maxPageSize}
   * (never rejected).
   */
  public int resolveLimit(@Nullable Integer requested) {
    if (requested == null || requested < 1) {
      return defaultPageSize;
    }
    return Math.min(requested, maxPageSize);
  }

  /** base64url(no-padding) of the id; null/blank id yields null. */
  public static @Nullable String encodeCursor(@Nullable String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
  }

  /** Inverse of {@link #encodeCursor}; a blank or malformed cursor decodes to null (first page). */
  public static @Nullable String decodeCursor(@Nullable String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return decoded.isBlank() ? null : decoded;
    } catch (IllegalArgumentException malformed) {
      return null;
    }
  }

  /**
   * Split an over-fetched list into a page. {@code fetched} must hold up to {@code limit + 1} rows
   * (the repository fetched one extra to detect a next page). When more than {@code limit} rows came
   * back, the surplus is dropped and {@code nextCursor} is the encoded id of the last kept row;
   * otherwise this is the last page and {@code nextCursor} is null.
   *
   * @param idExtractor reads the sortable TSID id from a row
   */
  public static <T> Page<T> paginate(List<T> fetched, int limit, Function<T, String> idExtractor) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be >= 1");
    }
    if (fetched.size() <= limit) {
      return new Page<>(fetched, null);
    }
    List<T> items = List.copyOf(fetched.subList(0, limit));
    String nextCursor = encodeCursor(idExtractor.apply(items.get(items.size() - 1)));
    return new Page<>(items, nextCursor);
  }
}
