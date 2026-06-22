package com.example.commerce.security;

/**
 * Validation seam for the gate-2 edge filters: maps a raw bearer token to a {@link CommercePrincipal}
 * or throws {@link InvalidTokenException}. The production implementation is the final
 * {@link CommerceJwtValidator}; tests stub this functional interface to exercise the invalid-token
 * branch without a real JWKS.
 *
 * <p>Shared by the cart, catalog, and order principal filters so each injects this seam directly
 * instead of carrying its own per-service copy. (payment-service validates a service JWT into a
 * {@code ServicePrincipal} and keeps its own seam.)
 */
@FunctionalInterface
public interface TokenValidator {

  CommercePrincipal validate(String token);
}
