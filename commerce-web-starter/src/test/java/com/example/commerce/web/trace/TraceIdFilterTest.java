package com.example.commerce.web.trace;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

  private final TraceIdFilter filter = new TraceIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void copiesHeaderIntoMdcDuringRequestAndRemovesAfter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] seenDuringChain = new String[1];
    FilterChain chain = (req, res) -> seenDuringChain[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

    filter.doFilter(request, response, chain);

    assertThat(seenDuringChain[0]).isEqualTo("trace-abc"); // present inside the chain
    assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull(); // cleared in finally
  }

  @Test
  void clearsMdcEvenWhenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "trace-boom");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {
      throw new RuntimeException("boom");
    };

    try {
      filter.doFilter(request, response, chain);
    } catch (Exception expected) {
      // swallow; we only assert MDC was cleaned up
    }

    assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
  }

  @Test
  void missingHeaderLeavesMdcUntouched() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(); // no X-Trace-Id
    MockHttpServletResponse response = new MockHttpServletResponse();

    String[] seenDuringChain = new String[] {"sentinel"};
    FilterChain chain = (req, res) -> seenDuringChain[0] = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);

    filter.doFilter(request, response, chain);

    assertThat(seenDuringChain[0]).isNull();
    assertThat(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)).isNull();
  }
}
