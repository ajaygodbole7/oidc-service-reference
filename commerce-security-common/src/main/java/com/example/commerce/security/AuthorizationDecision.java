package com.example.commerce.security;

/** Result returned by the resource-authorization port. */
public record AuthorizationDecision(boolean allowed, DecisionTrace trace) {

  public static AuthorizationDecision allow(DecisionTrace trace) {
    return new AuthorizationDecision(true, trace);
  }

  public static AuthorizationDecision deny(DecisionTrace trace) {
    return new AuthorizationDecision(false, trace);
  }
}
