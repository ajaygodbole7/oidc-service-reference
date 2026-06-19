package com.example.commerce.cart.web;

import com.example.commerce.cart.service.CartNotFoundException;
import com.example.commerce.security.AuthorizationDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class RestExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(AuthorizationDeniedException.class)
  ResponseEntity<ProblemDetail> authorizationDenied(AuthorizationDeniedException exception) {
    return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
  }

  @ExceptionHandler(CartNotFoundException.class)
  ResponseEntity<ProblemDetail> cartNotFound(CartNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, "Cart not found", exception.getMessage());
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
        "unexpected cart-service error");
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
}
