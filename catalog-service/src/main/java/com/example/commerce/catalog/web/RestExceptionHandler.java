package com.example.commerce.catalog.web;

import com.example.commerce.catalog.service.ProductNotFoundException;
import com.example.commerce.security.AuthorizationDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class RestExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(RestExceptionHandler.class);

  @ExceptionHandler(AuthorizationDeniedException.class)
  ResponseEntity<ProblemDetail> authorizationDenied(AuthorizationDeniedException exception) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ProblemDetail> productNotFound(ProductNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Product not found", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
    return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "unexpected catalog-service error");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemObject(HttpStatus.BAD_REQUEST, "Invalid request", "request validation failed");
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problemObject(HttpStatus.UNAUTHORIZED, "Unauthorized", "missing authenticated principal");
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    LOG.warn("catalog request body was unreadable: {}", boundedCause(exception));
    return problemObject(HttpStatus.BAD_REQUEST, "Invalid request", "request body was unreadable");
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String message) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    return ResponseEntity
        .status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(detail);
  }

  private static ResponseEntity<Object> problemObject(HttpStatus status, String title, String message) {
    return ResponseEntity
        .status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problemDetail(status, title, message));
  }

  private static ProblemDetail problemDetail(HttpStatus status, String title, String message) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    return detail;
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
}
