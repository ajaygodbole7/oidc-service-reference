package com.example.commerce.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Detection rules for the shared boot-guard sentinel helper. */
class SecretSentinelTest {

  @Test
  void containsSentinel_detectsMarkerSubstring() {
    assertThat(SecretSentinel.containsSentinel("LOCAL_DEV_POSTGRES_PASSWORD__CHANGE_BEFORE_DEPLOY"))
        .isTrue();
  }

  @Test
  void containsSentinel_falseForRealSecretAndNull() {
    assertThat(SecretSentinel.containsSentinel("a-real-rotated-secret")).isFalse();
    assertThat(SecretSentinel.containsSentinel(null)).isFalse();
  }

  @Test
  void isLocalProfile_falseWhenNoActiveProfile() {
    // No active profile is NOT local: a copied artifact must fail closed rather than ship a
    // dev sentinel with only a WARN (SECURITY.md §D-1 — unsafe-by-omission).
    assertThat(SecretSentinel.isLocalProfile(new String[] {})).isFalse();
  }

  @Test
  void isLocalProfile_trueForAllowListedProfiles_caseInsensitive() {
    assertThat(SecretSentinel.isLocalProfile(new String[] {"local"})).isTrue();
    assertThat(SecretSentinel.isLocalProfile(new String[] {"DEV"})).isTrue();
    assertThat(SecretSentinel.isLocalProfile(new String[] {"test"})).isTrue();
    assertThat(SecretSentinel.isLocalProfile(new String[] {"local", "test"})).isTrue();
  }

  @Test
  void isLocalProfile_falseWhenAnyProfileIsNonLocal() {
    assertThat(SecretSentinel.isLocalProfile(new String[] {"staging"})).isFalse();
    assertThat(SecretSentinel.isLocalProfile(new String[] {"prod"})).isFalse();
    // A non-local profile alongside a local one is still non-local.
    assertThat(SecretSentinel.isLocalProfile(new String[] {"local", "prod"})).isFalse();
  }
}
