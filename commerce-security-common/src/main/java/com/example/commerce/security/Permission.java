package com.example.commerce.security;

/** Fine-grained resource permission, for example read, write, cancel, or manage. */
public record Permission(String value) {

  public Permission {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("permission value is required");
    }
  }
}
