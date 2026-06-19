package com.example.commerce.security;

/**
 * Thrown when a scope or resource gate denies. Callers must map this to a denied request,
 * never a fall-through success.
 */
public final class AuthorizationDeniedException extends RuntimeException {

  private final DecisionTrace trace;

  public AuthorizationDeniedException(String message, DecisionTrace trace) {
    super(message);
    this.trace = trace;
  }

  public AuthorizationDeniedException(String message, DecisionTrace trace, Throwable cause) {
    super(message, cause);
    this.trace = trace;
  }

  public DecisionTrace trace() {
    return trace;
  }
}
