/**
 * Shared security primitives for the commerce platform: gate-2 JWT validation
 * ({@link com.example.commerce.security.CommerceJwtValidator}), the authenticated
 * {@link com.example.commerce.security.CommercePrincipal}, scope/resource
 * authorizers, the {@link com.example.commerce.security.AuthorizationClient} port,
 * the {@link com.example.commerce.security.SpiceDbAuthorizationClient} adapter, and
 * decision traces.
 *
 * <p>Raw JWTs stay at this boundary; domain objects never receive raw tokens.
 */
@NullMarked
package com.example.commerce.security;

import org.jspecify.annotations.NullMarked;
