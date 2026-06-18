package com.example.commerce.security;

/** Subject reference used by the authorization port, for example user:alice. */
public record SubjectRef(String type, String id) {

  public SubjectRef {
    require(type, "subject type");
    require(id, "subject id");
  }

  public static SubjectRef user(String sub) {
    return new SubjectRef("user", sub);
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
