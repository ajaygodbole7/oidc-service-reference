package com.example.commerce.security;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shared, dependency-light detection for the "unsafe-by-omission" boot guard. The reference
 * repo seeds dev defaults for confidential secrets (datasource passwords, an inter-service
 * client secret) so the stack is zero-config locally; each default carries the marker
 * {@value #MARKER} so a text search across application.yml, compose.yaml, and the realm file
 * surfaces every value a deployment must rotate before going live.
 *
 * <p>This class is the pure detector shared by every service's boot-time guard, so the marker
 * string and the local-profile rule live in exactly one place rather than copied into each
 * service. It is intentionally Spring-free (this module has no Spring dependency); the
 * per-service guard injects {@code Environment} and passes {@code env.getActiveProfiles()} to
 * {@link #isLocalProfile(String[])}.
 */
public final class SecretSentinel {

  /**
   * Marker embedded in every committed dev-default secret. A non-local boot that still sees
   * this in a secret value is running a committed placeholder and must fail closed.
   */
  public static final String MARKER = "CHANGE_BEFORE_DEPLOY";

  // Profiles treated as inner-loop local dev. Anything else — staging, uat, prod, or any
  // custom environment name — is non-local and fails closed. Mirrors auth-service's
  // SecretSentinelValidator.LOCAL_PROFILES.
  private static final List<String> LOCAL_PROFILES = List.of("local", "dev", "test");

  private SecretSentinel() {}

  /** True when {@code value} is non-null and carries the dev sentinel marker. */
  public static boolean containsSentinel(@Nullable String value) {
    return value != null && value.contains(MARKER);
  }

  /**
   * Local ONLY when at least one profile is active and every active profile is in the local
   * allow-list ({@code local}/{@code dev}/{@code test}, case-insensitive). An empty array is
   * NOT local: a copied artifact run without an explicit local/dev/test opt-in must fail
   * closed rather than ship a dev sentinel with only a WARN (SECURITY.md §D-1 —
   * unsafe-by-omission). Mirrors auth-service's SecretSentinelValidator.isLocalProfile().
   */
  public static boolean isLocalProfile(String[] activeProfiles) {
    if (activeProfiles.length == 0) {
      return false;
    }
    for (String profile : activeProfiles) {
      if (!LOCAL_PROFILES.contains(profile.toLowerCase())) {
        return false;
      }
    }
    return true;
  }
}
