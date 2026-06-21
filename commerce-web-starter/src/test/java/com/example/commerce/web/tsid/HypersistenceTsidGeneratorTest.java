package com.example.commerce.web.tsid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HypersistenceTsidGeneratorTest {

  private final TsidGenerator generator = new HypersistenceTsidGenerator();

  @Test
  void newId_is13CharCrockfordBase32() {
    String id = generator.newId();
    assertThat(id).hasSize(13);
    // Crockford base32 alphabet: 0-9 A-Z minus I L O U.
    assertThat(id).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{13}");
  }

  @Test
  void newId_isUniqueAndLexicallySortableAcrossTime() throws InterruptedException {
    String earlier = generator.newId();
    Thread.sleep(5);
    String later = generator.newId();
    // Fixed width + time prefix => later id sorts lexically after the earlier id.
    assertThat(later.compareTo(earlier)).isGreaterThan(0);
    assertThat(earlier).hasSameSizeAs(later);
  }

  @Test
  void newId_generatesDistinctIds() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 1_000; i++) {
      ids.add(generator.newId());
    }
    assertThat(ids).hasSize(1_000);
  }
}
