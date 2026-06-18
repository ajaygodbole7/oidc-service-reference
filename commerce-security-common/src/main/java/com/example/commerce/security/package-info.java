/**
 * Shared security primitives for the commerce platform: gate-2 JWT validation
 * ({@link com.example.commerce.security.CommerceJwtValidator}), the authenticated
 * {@link com.example.commerce.security.CommercePrincipal}, and (added in later
 * increments) the scope/resource authorizers and decision trace.
 *
 * <p>Raw JWTs stay at this boundary; domain objects never receive raw tokens.
 */
@NullMarked
package com.example.commerce.security;

import org.jspecify.annotations.NullMarked;
