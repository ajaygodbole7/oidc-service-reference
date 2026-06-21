package com.example.commerce.web.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the enriched RFC 9457 {@link ProblemDetail} shape directly to a servlet response from inside
 * a filter — the path that never reaches the {@link GlobalExceptionHandler} advice (the advice only
 * sees exceptions raised within the dispatcher, not a filter that short-circuits the chain).
 *
 * <p>The body is built by the shared {@link ProblemDetailFactory}, so a filter-emitted 401 and an
 * advice-emitted 401 carry the same {@code type}, {@code errorCode}, {@code traceId}, and
 * {@code timestamp}. This writer sets the status and {@code application/problem+json} content type;
 * the caller (the auth filter) keeps responsibility for any {@code WWW-Authenticate} header.
 */
public class ProblemDetailWriter {

  private final ProblemDetailFactory factory;
  private final ObjectMapper objectMapper;

  public ProblemDetailWriter(ProblemDetailFactory factory, ObjectMapper objectMapper) {
    this.factory = factory;
    this.objectMapper = objectMapper;
  }

  /**
   * Serializes an enriched problem detail to the response. Sets the status code and
   * {@code application/problem+json} content type; does not touch {@code WWW-Authenticate}.
   */
  public void write(HttpServletResponse response, HttpStatus status, String slug, String title, String detail)
      throws IOException {
    ProblemDetail problem = factory.of(status, slug, title, detail);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(problem));
  }
}
