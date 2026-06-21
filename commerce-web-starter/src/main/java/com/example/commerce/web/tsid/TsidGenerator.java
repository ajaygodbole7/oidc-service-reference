package com.example.commerce.web.tsid;

/**
 * Produces a 13-char Crockford base32 TSID string. Fixed-width and time-sortable: ids minted later
 * sort lexically after ids minted earlier, so {@code ORDER BY id} / {@code WHERE id > :cursor} are a
 * stable keyset. This replaces {@code UUID.randomUUID()} and {@code "pay_" + UUID} at every id site.
 *
 * <p>An interface so services inject it and tests can stub a deterministic sequence; the
 * auto-configured bean is {@link HypersistenceTsidGenerator}.
 */
@FunctionalInterface
public interface TsidGenerator {

  /** A fresh 13-char TSID string. */
  String newId();
}
