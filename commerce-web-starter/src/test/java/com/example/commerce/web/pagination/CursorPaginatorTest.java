package com.example.commerce.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPaginatorTest {

  private final CursorPaginator paginator = new CursorPaginator(20, 100);

  @Test
  void encodeDecode_roundTrips() {
    String id = "0GZ8YQ1234567";
    String cursor = CursorPaginator.encodeCursor(id);
    assertThat(cursor).isNotNull().isNotEqualTo(id); // opaque, not the raw id
    assertThat(CursorPaginator.decodeCursor(cursor)).isEqualTo(id);
  }

  @Test
  void decode_blankOrMalformedIsFirstPage() {
    assertThat(CursorPaginator.decodeCursor(null)).isNull();
    assertThat(CursorPaginator.decodeCursor("")).isNull();
    assertThat(CursorPaginator.decodeCursor("   ")).isNull();
    assertThat(CursorPaginator.decodeCursor("!!!not base64!!!")).isNull();
  }

  @Test
  void resolveLimit_defaultsAndCaps() {
    assertThat(paginator.resolveLimit(null)).isEqualTo(20); // default
    assertThat(paginator.resolveLimit(0)).isEqualTo(20); // non-positive -> default
    assertThat(paginator.resolveLimit(-5)).isEqualTo(20);
    assertThat(paginator.resolveLimit(10)).isEqualTo(10); // within range
    assertThat(paginator.resolveLimit(5_000)).isEqualTo(100); // clamped to max, not rejected
  }

  @Test
  void paginate_overFetchYieldsNextCursor() {
    // limit 2, repository fetched limit+1 = 3 rows.
    List<String> fetched = List.of("AAA", "BBB", "CCC");
    Page<String> page = CursorPaginator.paginate(fetched, 2, id -> id);

    assertThat(page.items()).containsExactly("AAA", "BBB"); // surplus row dropped
    assertThat(page.hasNext()).isTrue();
    assertThat(page.nextCursor()).isEqualTo(CursorPaginator.encodeCursor("BBB")); // last kept row's id
    assertThat(CursorPaginator.decodeCursor(page.nextCursor())).isEqualTo("BBB");
  }

  @Test
  void paginate_lastPageHasNoCursor() {
    List<String> fetched = List.of("AAA", "BBB"); // exactly limit, no surplus
    Page<String> page = CursorPaginator.paginate(fetched, 2, id -> id);

    assertThat(page.items()).containsExactly("AAA", "BBB");
    assertThat(page.hasNext()).isFalse();
    assertThat(page.nextCursor()).isNull();
  }
}
