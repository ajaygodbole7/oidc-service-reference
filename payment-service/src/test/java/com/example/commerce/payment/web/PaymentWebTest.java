package com.example.commerce.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.payment.domain.PaymentAuthorization;
import com.example.commerce.payment.domain.PaymentAuthorizationRepository;
import com.example.commerce.payment.service.PaymentApplicationService;
import com.example.commerce.security.InvalidTokenException;
import com.example.commerce.security.ServicePrincipal;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import com.example.commerce.web.tsid.TsidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class PaymentWebTest {

  private final RecordingRepository repository = new RecordingRepository();
  private final PaymentApplicationService service = new PaymentApplicationService(
      repository,
      tsidGenerator(),
      Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new PaymentController(service))
      .addFilters(new ServicePrincipalFilter(this::principalForToken))
      .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
      .setValidator(validator())
      .build();

  @Test
  void valid_order_service_call_authorizes_payment() throws Exception {
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // TSID, not the legacy "pay_" + UUID: 13-char Crockford base32, no prefix.
        .andExpect(jsonPath("$.paymentId").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("pay_"))))
        .andExpect(jsonPath("$.paymentId").value(org.hamcrest.Matchers.matchesPattern("[0-9A-HJKMNP-TV-Z]{13}")))
        .andExpect(jsonPath("$.status").value("AUTHORIZED"))
        .andExpect(jsonPath("$.orderId").value("order-1"));

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  @Test
  void user_commerce_api_token_is_rejected_before_service_runs() throws Exception {
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer user-commerce-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void wrong_client_token_is_rejected_before_service_runs() throws Exception {
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-client-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("invalid bearer token"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void missing_idempotency_key_returns_problem_json_before_save() throws Exception {
    // A missing required header is a BadRequestException -> 400 "Bad request" via the starter advice.
    // Post-Phase-2 reconciliation: this is 400 (not 422), with the full RFC 9457 shape.
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value(typeFor("idempotency-key-required")))
        .andExpect(jsonPath("$.title").value("Bad request"))
        .andExpect(jsonPath("$.detail").value("Idempotency-Key is required"))
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void invalid_body_returns_validation_problem_detail() throws Exception {
    // Blank/missing required fields fail bean validation -> MethodArgumentNotValidException
    // -> 400 "Invalid request" (slug validation-failed) via the starter advice.
    String invalidBody = """
        {
          "orderId": "",
          "userSub": "",
          "amount": null,
          "currency": ""
        }
        """;

    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidBody))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.type").value(typeFor("validation-failed")))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void unauthorized_caller_past_filter_returns_business_rule_problem_detail() throws Exception {
    // A token that VALIDATES (filter passes) but resolves to a principal that is not the
    // order-service caller. The service-layer defense-in-depth guard throws
    // PaymentAuthorizationException (extends BusinessRuleException) -> 422 with the full shape.
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-but-unauthorized-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.type").value(typeFor("payment-caller-not-authorized")))
        .andExpect(jsonPath("$.title").value("Business rule violation"))
        .andExpect(jsonPath("$.detail").value("unexpected payment caller"))
        .andExpect(jsonPath("$.errorCode").value("PAYMENT_CALLER_NOT_AUTHORIZED"));

    assertThat(repository.saveCount()).isZero();
  }

  @Test
  void idempotency_collision_returns_conflict_before_second_save() throws Exception {
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isOk());

    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .header("Idempotency-Key", "pay-key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "29.99")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  private ServicePrincipal principalForToken(String token) {
    return switch (token) {
      case "order-token" -> new ServicePrincipal("order-service", Set.of("payments:authorize"), "fp-order");
      // Validates fine (filter passes) but is the wrong S2S caller -> service guard fires (422).
      case "valid-but-unauthorized-token" ->
          new ServicePrincipal("cart-service", Set.of("payments:authorize"), "fp-cart");
      case "user-commerce-token" -> throw new InvalidTokenException("wrong audience commerce-api");
      case "wrong-client-token" -> throw new InvalidTokenException("unexpected service caller");
      default -> throw new InvalidTokenException("invalid token");
    };
  }

  /** The RFC 9457 {@code type} URI: base-url + "/" + slug, matching the GlobalExceptionHandler. */
  private static String typeFor(String slug) {
    return "https://errors.example.com/payment/" + slug;
  }

  private static String paymentJson(String orderId, String userSub, String amount) {
    return """
        {
          "orderId": "%s",
          "userSub": "%s",
          "amount": %s,
          "currency": "USD"
        }
        """.formatted(orderId, userSub, amount);
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  /** Fixed 13-char Crockford base32 TSID so the wire shape is asserted deterministically. */
  private static TsidGenerator tsidGenerator() {
    return () -> "0GZ4Y2K1P3M7Q";
  }

  private static CommerceErrorProperties errorProperties() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/payment");
    return properties;
  }

  private static final class RecordingRepository implements PaymentAuthorizationRepository {

    private final Map<String, PaymentAuthorization> byIdempotencyKey = new LinkedHashMap<>();
    private int saveCount;

    @Override
    public Optional<PaymentAuthorization> findByIdempotencyKey(String idempotencyKey) {
      return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public PaymentAuthorization save(PaymentAuthorization authorization) {
      saveCount++;
      byIdempotencyKey.put(authorization.idempotencyKey(), authorization);
      return authorization;
    }

    int saveCount() {
      return saveCount;
    }
  }
}
