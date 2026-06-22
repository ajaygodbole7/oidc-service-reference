package com.example.commerce.cart.web;

import com.example.commerce.security.InvalidTokenException;
import com.example.commerce.security.TokenValidator;
import com.example.commerce.web.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate 2 for cart-service. APISIX injects the bearer token; this edge filter validates it
 * and exposes only the mapped CommercePrincipal to controllers.
 */
@Component
final class CommercePrincipalFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(CommercePrincipalFilter.class);
  private static final String ATTRIBUTE = "commercePrincipal";

  private final TokenValidator tokenValidator;
  private final ProblemDetailWriter problemDetailWriter;

  CommercePrincipalFilter(TokenValidator tokenValidator, ProblemDetailWriter problemDetailWriter) {
    this.tokenValidator = tokenValidator;
    this.problemDetailWriter = problemDetailWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.equals("/api/cart")
        && !path.startsWith("/api/cart/")
        && !path.startsWith("/api/carts/")
        && !path.startsWith("/api/_test/cart/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      unauthorized(response, "Bearer", "missing bearer token");
      return;
    }

    String token = authorization.substring("Bearer ".length()).trim();
    if (token.isEmpty()) {
      unauthorized(response, "Bearer", "missing bearer token");
      return;
    }

    try {
      request.setAttribute(ATTRIBUTE, tokenValidator.validate(token));
    } catch (InvalidTokenException exception) {
      LOG.warn("cart JWT validation failed: {}", boundedCause(exception));
      unauthorized(response, "Bearer error=\"invalid_token\"", "invalid bearer token");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static String boundedCause(Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    if (message == null || message.isBlank()) {
      return cause.getClass().getSimpleName();
    }
    return cause.getClass().getSimpleName() + ": " + message.replaceAll("[\\r\\n]+", " ");
  }

  private void unauthorized(
      HttpServletResponse response, String authenticateHeader, String detail) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeader);
    problemDetailWriter.write(response, HttpStatus.UNAUTHORIZED, "invalid-token", "Unauthorized", detail);
  }
}
