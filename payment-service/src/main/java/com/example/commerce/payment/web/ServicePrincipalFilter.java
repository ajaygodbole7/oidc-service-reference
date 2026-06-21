package com.example.commerce.payment.web;

import com.example.commerce.security.InvalidTokenException;
import com.example.commerce.security.ServiceJwtValidator;
import com.example.commerce.security.ServicePrincipal;
import com.example.commerce.web.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class ServicePrincipalFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ServicePrincipalFilter.class);
  private static final String ATTRIBUTE = "servicePrincipal";

  private final PaymentTokenValidator tokenValidator;
  private final ProblemDetailWriter problemDetailWriter;

  @Autowired
  ServicePrincipalFilter(ServiceJwtValidator validator, ProblemDetailWriter problemDetailWriter) {
    this.tokenValidator = validator::validate;
    this.problemDetailWriter = problemDetailWriter;
  }

  ServicePrincipalFilter(PaymentTokenValidator tokenValidator, ProblemDetailWriter problemDetailWriter) {
    this.tokenValidator = tokenValidator;
    this.problemDetailWriter = problemDetailWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/payments/");
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
      LOG.warn("payment S2S JWT validation failed: {}", boundedCause(exception));
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

  @FunctionalInterface
  interface PaymentTokenValidator {

    ServicePrincipal validate(String token);
  }
}
