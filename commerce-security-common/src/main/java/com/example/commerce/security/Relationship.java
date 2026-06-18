package com.example.commerce.security;

/** Test/seed relationship shape used before the real SpiceDB adapter exists. */
public record Relationship(ResourceRef resource, String relation, SubjectRef subject) {

  public Relationship {
    if (relation == null || relation.isBlank()) {
      throw new IllegalArgumentException("relation is required");
    }
  }
}
