package com.example.commerce.web.tsid;

import io.hypersistence.tsid.TSID;

/**
 * Default {@link TsidGenerator} backed by hypersistence-tsid. {@code TSID.Factory.getTsid()} is the
 * shared thread-safe monotonic factory; {@code toString()} renders the 13-char Crockford base32 form.
 */
public final class HypersistenceTsidGenerator implements TsidGenerator {

  @Override
  public String newId() {
    return TSID.Factory.getTsid().toString();
  }
}
