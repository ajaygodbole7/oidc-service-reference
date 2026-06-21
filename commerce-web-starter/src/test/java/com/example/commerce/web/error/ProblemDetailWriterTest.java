package com.example.commerce.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the writer serializes the SAME enriched RFC 9457 shape the advice emits: {@code type} =
 * base-url + slug, the {@code errorCode}/{@code traceId}/{@code timestamp} extensions,
 * title/status/detail, and {@code application/problem+json}. The {@link ProblemDetailJacksonMixin} is
 * registered so the extension map flattens to top-level fields, exactly as the Boot-configured
 * ObjectMapper does at runtime.
 */
class ProblemDetailWriterTest {

  // Matches the Boot-configured ObjectMapper: registers the mixin that flattens ProblemDetail
  // extensions (errorCode/traceId/timestamp) to top-level properties via @JsonAnyGetter.
  private final ObjectMapper objectMapper =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
  private final ProblemDetailWriter writer = new ProblemDetailWriter(factory(), objectMapper);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void writesFullEnrichedShapeWithTraceId() throws Exception {
    MDC.put("traceId", "trace-xyz");
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, HttpStatus.UNAUTHORIZED, "invalid-token", "Unauthorized", "invalid bearer token");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.get("type").asString()).isEqualTo("https://errors.example.com/catalog/invalid-token");
    assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("detail").asString()).isEqualTo("invalid bearer token");
    assertThat(body.get("errorCode").asString()).isEqualTo("INVALID_TOKEN");
    assertThat(body.get("traceId").asString()).isEqualTo("trace-xyz");
    assertThat(body.get("timestamp").asString()).isNotBlank();
  }

  @Test
  void omitsTraceIdWhenMdcUnset() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, HttpStatus.UNAUTHORIZED, "invalid-token", "Unauthorized", "missing bearer token");

    JsonNode body = objectMapper.readTree(response.getContentAsString());
    assertThat(body.has("traceId")).isFalse();
    assertThat(body.get("detail").asString()).isEqualTo("missing bearer token");
    assertThat(body.get("errorCode").asString()).isEqualTo("INVALID_TOKEN");
    assertThat(body.get("type").asString()).isEqualTo("https://errors.example.com/catalog/invalid-token");
  }

  private static ProblemDetailFactory factory() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/catalog");
    return new ProblemDetailFactory(properties);
  }
}
