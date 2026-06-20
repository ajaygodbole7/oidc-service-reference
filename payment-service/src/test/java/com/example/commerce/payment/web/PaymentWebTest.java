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
      Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new PaymentController(service))
      .addFilters(new ServicePrincipalFilter(this::principalForToken))
      .setControllerAdvice(new RestExceptionHandler())
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
        .andExpect(jsonPath("$.paymentId").value(org.hamcrest.Matchers.startsWith("pay_")))
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
    mockMvc.perform(post("/internal/payments/authorize")
            .header(HttpHeaders.AUTHORIZATION, "Bearer order-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(paymentJson("order-1", "alice", "19.99")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request"));

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
        .andExpect(jsonPath("$.title").value("Idempotency conflict"));

    assertThat(repository.saveCount()).isEqualTo(1);
  }

  private ServicePrincipal principalForToken(String token) {
    return switch (token) {
      case "order-token" -> new ServicePrincipal("order-service", Set.of("payments:authorize"), "fp-order");
      case "user-commerce-token" -> throw new InvalidTokenException("wrong audience commerce-api");
      case "wrong-client-token" -> throw new InvalidTokenException("unexpected service caller");
      default -> throw new InvalidTokenException("invalid token");
    };
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
