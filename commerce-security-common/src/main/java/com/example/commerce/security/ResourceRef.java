package com.example.commerce.security;

/** Resource reference used by the authorization port, for example cart:alice-cart. */
public record ResourceRef(String type, String id) {

  public ResourceRef {
    require(type, "resource type");
    require(id, "resource id");
  }

  private static void require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }

  @Override
  public String toString() {
    return type + ":" + id;
  }
}
