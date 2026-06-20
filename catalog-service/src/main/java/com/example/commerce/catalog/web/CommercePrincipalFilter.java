package com.example.commerce.catalog.web;

import com.example.commerce.security.CommerceJwtValidator;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gate 2 for catalog writes. Catalog reads are anonymous/read-only and deliberately skip
 * JWT validation; writes require APISIX's injected bearer token.
 */
@Component
final class CommercePrincipalFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(CommercePrincipalFilter.class);
  private static final String ATTRIBUTE = "commercePrincipal";

  private final CatalogTokenValidator tokenValidator;

  @Autowired
  CommercePrincipalFilter(CommerceJwtValidator validator) {
    this.tokenValidator = validator::validate;
  }

  CommercePrincipalFilter(CatalogTokenValidator tokenValidator) {
    this.tokenValidator = tokenValidator;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/catalog/products")
        || ("GET".equals(request.getMethod())
            && (path.equals("/api/catalog/products") || path.startsWith("/api/catalog/products/")));
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
      LOG.warn("catalog JWT validation failed: {}", boundedCause(exception));
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

  private static void unauthorized(
      HttpServletResponse response, String authenticateHeader, String detail) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, authenticateHeader);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write("""
        {"type":"about:blank","title":"Unauthorized","status":401,"detail":"%s"}\
        """.formatted(detail));
  }

  @FunctionalInterface
  interface CatalogTokenValidator {

    CommercePrincipal validate(String token);
  }
}
