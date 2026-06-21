package com.example.commerce.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.security.AuthorizationDeniedException;
import com.example.commerce.security.AuthorizationUnavailableException;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InvalidTokenException;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives the advice through a real MVC dispatch (standalone MockMvc + setControllerAdvice, matching
 * the repo's web-test convention) against a throwing controller. Asserts the problem-detail shape,
 * every status-family mapping, the security scrub (no SpiceDB trace / token text in the body), and
 * the traceId extension sourced from MDC.
 */
class GlobalExceptionHandlerTest {

  private static final CommerceErrorProperties PROPERTIES = baseUrl("https://errors.example.com/catalog");

  private final MockMvc mvc = MockMvcBuilders
      .standaloneSetup(new ThrowingController())
      .setControllerAdvice(new GlobalExceptionHandler(PROPERTIES))
      .build();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  // ---- shape + status families

  @Test
  void resourceNotFound_404_fullProblemShape() throws Exception {
    MDC.put("traceId", "trace-404");
    mvc.perform(get("/boom/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/product-not-found"))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("no such product"))
        .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
        .andExpect(jsonPath("$.traceId").value("trace-404"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void conflict_409() throws Exception {
    mvc.perform(get("/boom/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errorCode").value("CART_VERSION_CONFLICT"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/cart-version-conflict"));
  }

  @Test
  void businessRule_422() throws Exception {
    mvc.perform(get("/boom/business-rule"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
  }

  @Test
  void serviceUnavailable_503() throws Exception {
    mvc.perform(get("/boom/unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.errorCode").value("PAYMENT_GATEWAY_DOWN"));
  }

  // ---- security scrub: status correct, but NO SpiceDB trace / token text in the body

  @Test
  void authorizationDenied_403_safeMessageEchoed_traceScrubbed() throws Exception {
    // The message is the SAFE denial reason and IS echoed; the sensitive DecisionTrace is not.
    mvc.perform(get("/boom/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.detail").value("missing required scope cart:read"))
        .andExpect(jsonPath("$.errorCode").value("AUTHORIZATION_DENIED"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/authorization-denied"))
        // the decision trace (subject / resource / zedtoken sentinel) must NOT leak:
        .andExpect(content().string(Matchers.not(Matchers.containsString("zedtoken-abc123"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("user:alice"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("cart:c-1"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("spicedb permission check failed"))));
  }

  @Test
  void invalidToken_401_scrubbed() throws Exception {
    mvc.perform(get("/boom/invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("invalid token"))
        .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
        // token internals must NOT leak:
        .andExpect(content().string(Matchers.not(Matchers.containsString("eyJhbGci"))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("exp claim"))));
  }

  @Test
  void authorizationUnavailable_503_scrubbed() throws Exception {
    mvc.perform(get("/boom/authz-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.detail").value("authorization unavailable"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("10.0.0.5:50051"))));
  }

  // ---- runtime guards: fixed body, never echo the exception message

  @Test
  void illegalArgument_400_fixedDetail() throws Exception {
    mvc.perform(get("/boom/illegal-argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("invalid request"))
        .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/invalid-request"))
        // the raw exception message must NOT leak:
        .andExpect(content().string(Matchers.not(Matchers.containsString("secret-internal-arg"))));
  }

  @Test
  void optimisticLocking_409_fixedDetail() throws Exception {
    mvc.perform(get("/boom/optimistic-lock"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value("the resource was modified concurrently, please retry"))
        .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/catalog/concurrent-modification"));
  }

  // ---- framework overrides preserved

  @Test
  void unreadableBody_400_noStacktrace() throws Exception {
    mvc.perform(post("/boom/echo").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("request body was unreadable"))
        .andExpect(content().string(Matchers.not(Matchers.containsString("at com."))))
        .andExpect(content().string(Matchers.not(Matchers.containsString("Exception"))));
  }

  @Test
  void missingRequiredPrincipal_mapsToUnauthorized() throws Exception {
    // A missing required @RequestAttribute (the gateway-injected principal) raises
    // ServletRequestBindingException, which the handler maps to 401.
    mvc.perform(get("/boom/needs-principal"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("missing authenticated principal"));
  }

  // ---- fixtures

  private static CommerceErrorProperties baseUrl(String url) {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl(url);
    return properties;
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/boom/not-found")
    String notFound() {
      throw new ResourceNotFoundException("product-not-found", "no such product");
    }

    @GetMapping("/boom/conflict")
    String conflict() {
      throw new ConflictException("cart-version-conflict", "stale cart version");
    }

    @GetMapping("/boom/business-rule")
    String businessRule() {
      throw new BusinessRuleException("out-of-stock", "item is out of stock");
    }

    @GetMapping("/boom/unavailable")
    String unavailable() {
      throw new ServiceUnavailableException("payment-gateway-down", "gateway timeout");
    }

    @GetMapping("/boom/denied")
    String denied() {
      // message is the SAFE denial reason; the sensitive trace (subject/resource/zedtoken) is separate.
      DecisionTrace trace = new DecisionTrace(
          "resource",
          false,
          "spicedb permission check failed",
          Map.of("subject", "user:alice", "resource", "cart:c-1", "zedtoken", "zedtoken-abc123"));
      throw new AuthorizationDeniedException("missing required scope cart:read", trace);
    }

    @GetMapping("/boom/invalid-token")
    String invalidToken() {
      throw new InvalidTokenException("exp claim rejected for token eyJhbGci.payload.sig");
    }

    @GetMapping("/boom/authz-unavailable")
    String authzUnavailable() {
      throw new AuthorizationUnavailableException("spicedb unreachable at 10.0.0.5:50051");
    }

    @GetMapping("/boom/illegal-argument")
    String illegalArgument() {
      throw new IllegalArgumentException("secret-internal-arg must not be blank");
    }

    @GetMapping("/boom/optimistic-lock")
    String optimisticLock() {
      throw new org.springframework.dao.OptimisticLockingFailureException("row version 3 != 4");
    }

    @GetMapping("/boom/needs-principal")
    String needsPrincipal(@RequestAttribute String commercePrincipal) {
      return commercePrincipal;
    }

    @PostMapping("/boom/echo")
    String echo(@RequestBody Map<String, Object> body) {
      return body.toString();
    }
  }
}
