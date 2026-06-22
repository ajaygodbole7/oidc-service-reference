package com.example.commerce.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.security.InvalidTokenException;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.ProblemDetailFactory;
import com.example.commerce.web.error.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives the filter directly for both 401 branches: missing bearer, and an invalid token rejected by
 * the validator. Both assert the enriched RFC 9457 shape shared with the advice: {@code type} =
 * base-url + slug, {@code errorCode}, {@code traceId} from MDC, and {@code application/problem+json},
 * with the {@code WWW-Authenticate} header preserved. The invalid-token branch is stubbed through the
 * shared {@code TokenValidator} seam the filter injects (production wires the final
 * {@code CommerceJwtValidator}, which implements it).
 */
class CommercePrincipalFilterTest {

  private static final JsonMapper MAPPER =
      JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

  // Validator is never invoked on the missing-bearer branch.
  private final CommercePrincipalFilter filter =
      new CommercePrincipalFilter(null, problemDetailWriter());

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void missingBearerToken_returnsEnrichedProblem401() throws Exception {
    MDC.put("traceId", "trace-order-1");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/o-1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] chainCalled = {false};
    FilterChain chain = (req, res) -> chainCalled[0] = true;

    filter.doFilter(request, response, chain);

    assertThat(chainCalled[0]).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");

    JsonNode body = MAPPER.readTree(response.getContentAsString());
    assertThat(body.get("type").asString()).isEqualTo("https://errors.example.com/order/invalid-token");
    assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("detail").asString()).isEqualTo("missing bearer token");
    assertThat(body.get("errorCode").asString()).isEqualTo("INVALID_TOKEN");
    assertThat(body.get("traceId").asString()).isEqualTo("trace-order-1");
  }

  @Test
  void invalidBearerToken_returnsEnrichedProblem401() throws Exception {
    MDC.put("traceId", "trace-order-2");
    CommercePrincipalFilter rejectingFilter = new CommercePrincipalFilter(
        token -> {
          throw new InvalidTokenException("bad token");
        },
        problemDetailWriter());
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/o-1");
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean[] chainCalled = {false};
    FilterChain chain = (req, res) -> chainCalled[0] = true;

    rejectingFilter.doFilter(request, response, chain);

    assertThat(chainCalled[0]).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer error=\"invalid_token\"");

    JsonNode body = MAPPER.readTree(response.getContentAsString());
    assertThat(body.get("type").asString()).isEqualTo("https://errors.example.com/order/invalid-token");
    assertThat(body.get("status").asInt()).isEqualTo(401);
    assertThat(body.get("detail").asString()).isEqualTo("invalid bearer token");
    assertThat(body.get("errorCode").asString()).isEqualTo("INVALID_TOKEN");
    assertThat(body.get("traceId").asString()).isEqualTo("trace-order-2");
  }

  private static ProblemDetailWriter problemDetailWriter() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/order");
    return new ProblemDetailWriter(new ProblemDetailFactory(properties), MAPPER);
  }
}
