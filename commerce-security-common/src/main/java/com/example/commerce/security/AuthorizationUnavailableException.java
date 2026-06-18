package com.example.commerce.security;

/** Raised by authorization adapters when the backing authority is unavailable. */
public final class AuthorizationUnavailableException extends RuntimeException {

  public AuthorizationUnavailableException(String message) {
    super(message);
  }

  public AuthorizationUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
