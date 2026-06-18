package com.example.commerce.security;

import java.util.Map;

/**
 * Bounded evidence for harness/security traces. Values are identifiers, fingerprints, and
 * decision reasons only; never raw tokens, cookies, secrets, or prompts.
 */
public record DecisionTrace(
    String gate,
    boolean allowed,
    String reason,
    Map<String, String> evidence) {

  public DecisionTrace {
    evidence = Map.copyOf(evidence);
  }

  public static DecisionTrace scope(
      boolean allowed, String scope, String subject, String tokenFingerprint) {
    return new DecisionTrace("scope", allowed, allowed ? "scope_present" : "scope_missing", Map.of(
        "scope", scope,
        "subject", subject,
        "token_fingerprint", tokenFingerprint));
  }

  public static DecisionTrace resource(
      boolean allowed, SubjectRef subject, ResourceRef resource, Permission permission, String reason) {
    return new DecisionTrace("resource", allowed, reason, Map.of(
        "subject", subject.toString(),
        "resource", resource.toString(),
        "permission", permission.value()));
  }
}
