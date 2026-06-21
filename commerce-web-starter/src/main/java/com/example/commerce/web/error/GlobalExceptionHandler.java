package com.example.commerce.web.error;

import com.example.commerce.security.AuthorizationDeniedException;
import com.example.commerce.security.AuthorizationUnavailableException;
import com.example.commerce.security.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
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

/**
 * The single, auto-configured RFC 9457 advice for every commerce service. Maps the shared
 * {@link ApiException} hierarchy and the security exceptions to {@link ProblemDetail}, with the
 * {@code errorCode}/{@code traceId}/{@code timestamp} extensions, and centralizes logging by status
 * family. Sensitive security internals are NEVER echoed: token internals and the SpiceDB decision
 * trace are logged server-side at WARN, while only the already-safe denial reason
 * ({@link AuthorizationDeniedException#getMessage()}) reaches the body.
 *
 * <p>Services delete their copied {@code RestExceptionHandler} and extend the base exceptions
 * instead; this advice then maps their domain exceptions with zero per-service handler code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String TRACE_ID_MDC_KEY = "traceId";

  private final ProblemDetailFactory problemDetailFactory;

  public GlobalExceptionHandler(CommerceErrorProperties properties) {
    this(new ProblemDetailFactory(properties));
  }

  public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
    this.problemDetailFactory = problemDetailFactory;
  }

  // ---- shared domain hierarchy: status follows the sealed family, slug + message come from the throw

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> resourceNotFound(ResourceNotFoundException exception) {
    return mapped(HttpStatus.NOT_FOUND, "Resource not found", exception.slug(), exception.getMessage(), exception);
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ProblemDetail> badRequest(BadRequestException exception) {
    return mapped(HttpStatus.BAD_REQUEST, "Bad request", exception.slug(), exception.getMessage(), exception);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ProblemDetail> conflict(ConflictException exception) {
    return mapped(HttpStatus.CONFLICT, "Conflict", exception.slug(), exception.getMessage(), exception);
  }

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ProblemDetail> businessRule(BusinessRuleException exception) {
    return mapped(
        HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", exception.slug(), exception.getMessage(), exception);
  }

  @ExceptionHandler(ServiceUnavailableException.class)
  public ResponseEntity<ProblemDetail> serviceUnavailable(ServiceUnavailableException exception) {
    return mapped(
        HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", exception.slug(), exception.getMessage(), exception);
  }

  // ---- security exceptions: safe denial reason in the body; trace / token internals logged only

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ProblemDetail> authorizationDenied(AuthorizationDeniedException exception) {
    // The message is the safe denial reason (e.g. "missing required scope cart:read"); the sensitive
    // SpiceDB decision trace is the separate .trace() field, logged server-side and never echoed.
    LOG.warn(
        "authorization denied [trace={}]: {} (decision={})",
        currentTraceId(),
        exception.getMessage(),
        exception.trace(),
        exception);
    return scrubbed(HttpStatus.FORBIDDEN, "Forbidden", "authorization-denied", exception.getMessage());
  }

  @ExceptionHandler(AuthorizationUnavailableException.class)
  public ResponseEntity<ProblemDetail> authorizationUnavailable(AuthorizationUnavailableException exception) {
    LOG.error(
        "authorization authority unavailable [trace={}]: {}", currentTraceId(), exception.getMessage(), exception);
    return scrubbed(
        HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", "authorization-unavailable", "authorization unavailable");
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ProblemDetail> invalidToken(InvalidTokenException exception) {
    LOG.warn("invalid token [trace={}]: {}", currentTraceId(), exception.getMessage(), exception);
    return scrubbed(HttpStatus.UNAUTHORIZED, "Unauthorized", "invalid-token", "invalid token");
  }

  // ---- runtime guards: fixed detail, never echo the exception message

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException exception) {
    // Argument-validation failures may carry caller-supplied or internal detail; keep the body fixed.
    log(HttpStatus.BAD_REQUEST, "invalid request", exception);
    return scrubbed(HttpStatus.BAD_REQUEST, "Bad request", "invalid-request", "invalid request");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> optimisticLocking(OptimisticLockingFailureException exception) {
    log(HttpStatus.CONFLICT, "concurrent modification", exception);
    return scrubbed(
        HttpStatus.CONFLICT,
        "Conflict",
        "concurrent-modification",
        "the resource was modified concurrently, please retry");
  }

  // ---- last-resort: never leak an internal message or a stacktrace into the body

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception exception) {
    LOG.error("unexpected error [trace={}]", currentTraceId(), exception);
    return body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "internal-error", "unexpected error"));
  }

  // ---- framework overrides: keep them, route through the same shape + logging

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    log(HttpStatus.BAD_REQUEST, "request validation failed", exception);
    return object(problem(HttpStatus.BAD_REQUEST, "Invalid request", "validation-failed", "request validation failed"));
  }

  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    log(HttpStatus.UNAUTHORIZED, "missing authenticated principal", exception);
    return object(
        problem(HttpStatus.UNAUTHORIZED, "Unauthorized", "missing-principal", "missing authenticated principal"));
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    log(HttpStatus.BAD_REQUEST, "request body was unreadable", exception);
    return object(problem(HttpStatus.BAD_REQUEST, "Invalid request", "unreadable-body", "request body was unreadable"));
  }

  // ---- builders

  private ResponseEntity<ProblemDetail> mapped(
      HttpStatus status, String title, String slug, String detail, Exception exception) {
    log(status, detail, exception);
    return body(problem(status, title, slug, detail));
  }

  private ResponseEntity<ProblemDetail> scrubbed(HttpStatus status, String title, String slug, String fixedDetail) {
    return body(problem(status, title, slug, fixedDetail));
  }

  private ProblemDetail problem(HttpStatus status, String title, String slug, String detail) {
    return problemDetailFactory.of(status, slug, title, detail);
  }

  private static @org.jspecify.annotations.Nullable String currentTraceId() {
    return MDC.get(TRACE_ID_MDC_KEY);
  }

  /** 404 -> INFO, other 4xx -> WARN, 5xx -> ERROR with stacktrace. Never the body. */
  private static void log(HttpStatus status, String detail, Throwable exception) {
    if (status == HttpStatus.NOT_FOUND) {
      LOG.info("{} [trace={}]: {}", status.value(), currentTraceId(), detail);
    } else if (status.is4xxClientError()) {
      LOG.warn("{} [trace={}]: {}", status.value(), currentTraceId(), detail);
    } else {
      LOG.error("{} [trace={}]: {}", status.value(), currentTraceId(), detail, exception);
    }
  }

  private static ResponseEntity<ProblemDetail> body(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static ResponseEntity<Object> object(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
