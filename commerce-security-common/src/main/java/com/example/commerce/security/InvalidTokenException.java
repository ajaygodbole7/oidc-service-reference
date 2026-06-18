package com.example.commerce.security;

/**
 * Thrown when a service JWT fails gate-2 validation. The contract is fail-closed: any
 * caller that catches this must deny the request, never fall through to the resource.
 */
public final class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }

  public InvalidTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
