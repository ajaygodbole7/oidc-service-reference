package com.example.commerce.web.error;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Single source of truth for the enriched RFC 9457 {@link ProblemDetail} shape used across every
 * commerce service. Builds the {@code type} URI from {@link CommerceErrorProperties#getBaseUrl()} and
 * the per-error slug, sets the {@code title}/{@code status}/{@code detail}, and attaches the shared
 * extensions: {@code errorCode} (slug upper-snake-cased), {@code traceId} (from the MDC key set by the
 * trace filter, when present), and {@code timestamp}.
 *
 * <p>Both the {@link GlobalExceptionHandler} advice and the per-service auth filters (via
 * {@link ProblemDetailWriter}) go through this factory so an advice-emitted 401 and a filter-emitted
 * 401 carry byte-identical {@code type}/{@code errorCode}/extension shape.
 */
public class ProblemDetailFactory {

  private static final String TRACE_ID_MDC_KEY = "traceId";

  private final CommerceErrorProperties properties;

  public ProblemDetailFactory(CommerceErrorProperties properties) {
    this.properties = properties;
  }

  /**
   * Builds the enriched problem detail. The {@code traceId} extension is added only when the MDC key
   * is set (so a request that ran without a trace id simply omits it).
   */
  public ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create(typeFor(slug)));
    problem.setProperty("errorCode", errorCode(slug));
    problem.setProperty("timestamp", Instant.now().toString());
    String traceId = MDC.get(TRACE_ID_MDC_KEY);
    if (traceId != null) {
      problem.setProperty("traceId", traceId);
    }
    return problem;
  }

  private String typeFor(String slug) {
    String base = properties.getBaseUrl();
    if (base.endsWith("/")) {
      return base + slug;
    }
    return base + "/" + slug;
  }

  private static String errorCode(String slug) {
    return slug.toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
