package com.example.commerce.order.web;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Drives the filter directly for the missing-bearer 401 (the branch that returns before touching the
 * validator), asserting the enriched RFC 9457 shape shared with the advice: {@code type} =
 * base-url + slug, {@code errorCode}, {@code traceId} from MDC, and {@code application/problem+json},
 * with the {@code WWW-Authenticate} header preserved. The invalid-token branch needs a stubbable
 * {@code CommerceJwtValidator} (a final class here), so its enriched shape is proven by catalog's and
 * payment's filter tests, which have a functional-interface seam.
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

  private static ProblemDetailWriter problemDetailWriter() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/order");
    return new ProblemDetailWriter(new ProblemDetailFactory(properties), MAPPER);
  }
}
