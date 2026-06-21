package com.example.commerce.web.pagination;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One keyset page: the items to return and an opaque {@code nextCursor} (null when this is the last
 * page). Hand {@code nextCursor} back as the {@code cursor} query param for the next page.
 */
public record Page<T>(List<T> items, @Nullable String nextCursor) {

  public Page {
    items = List.copyOf(items);
  }

  public boolean hasNext() {
    return nextCursor != null;
  }
}
