package com.example.commerce.security;

/**
 * Read-side consistency requirement for authorization queries (Check and LookupResources).
 *
 * <p>Kept SDK-free on purpose: services choose a <em>requirement</em>, and the SpiceDB adapter maps
 * it to a concrete SpiceDB {@code Consistency} (and supplies its own ZedToken for read-your-writes).
 * The in-memory fake is strongly consistent by construction and ignores the mode.
 *
 * <ul>
 *   <li>{@link #FULLY_CONSISTENT} — evaluate at the freshest revision. Use for security-critical
 *       point gates and mutation gates where a stale allow is unacceptable (e.g. cancel).
 *   <li>{@link #READ_YOUR_WRITES} — evaluate at least as fresh as this process's most recent
 *       relationship write (a Zookie/ZedToken high-water mark). Guarantees a just-written grant is
 *       visible on the next read without paying full-consistency cost on every call; falls back to
 *       {@link #MINIMIZE_LATENCY} when no write has happened yet.
 *   <li>{@link #MINIMIZE_LATENCY} — evaluate at any recent cached revision (fastest, eventually
 *       consistent). Use for non-critical reads that tolerate brief staleness.
 * </ul>
 */
public enum ReadConsistency {
  FULLY_CONSISTENT,
  READ_YOUR_WRITES,
  MINIMIZE_LATENCY
}
