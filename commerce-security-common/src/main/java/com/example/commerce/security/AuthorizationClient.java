package com.example.commerce.security;

/**
 * Port for fine-grained resource authorization. Implementations may be a test fake or a
 * SpiceDB adapter, but service code depends only on this interface.
 */
public interface AuthorizationClient {

  AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission);
}
