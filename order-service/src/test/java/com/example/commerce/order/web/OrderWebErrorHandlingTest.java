package com.example.commerce.order.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.OrderStatus;
import com.example.commerce.order.domain.ProductId;
import com.example.commerce.order.service.CartLookup;
import com.example.commerce.order.service.CartSnapshot;
import com.example.commerce.order.service.IdempotencyRecord;
import com.example.commerce.order.service.IdempotencyRepository;
import com.example.commerce.order.service.OrderApplicationService;
import com.example.commerce.order.service.OrderCheckoutPersistence;
import com.example.commerce.order.service.PaymentAuthorization;
import com.example.commerce.order.service.PaymentAuthorizationCommand;
import com.example.commerce.order.service.PaymentClient;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.Permission;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import com.example.commerce.web.error.CommerceErrorProperties;
import com.example.commerce.web.error.GlobalExceptionHandler;
import com.example.commerce.web.pagination.CursorPaginator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class OrderWebErrorHandlingTest {

  private static final Instant NOW = Instant.parse("2026-06-20T00:00:00Z");

  private final RecordingOrderRepository orderRepository =
      new RecordingOrderRepository(List.of(aliceOrder(), aliceCancelledOrder()));
  private final RecordingIdempotencyRepository idempotencyRepository = new RecordingIdempotencyRepository();
  private final OrderApplicationService service = new OrderApplicationService(
      orderRepository,
      subject -> Optional.of(new CartSnapshot(new CartId(subject + "-cart"), List.of(
          new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50"))))),
      idempotencyRepository,
      new OrderCheckoutPersistence(orderRepository, idempotencyRepository),
      new RecordingPaymentClient(),
      new ScopeAuthorizer(),
      new ResourceAuthorizer(new AllowingAuthorizationClient()),
      new CursorPaginator(20, 100),
      () -> new OrderId("generated-order"),
      Clock.fixed(NOW, ZoneOffset.UTC));
  private final MockMvc mockMvc = MockMvcBuilders
      .standaloneSetup(new OrderController(service))
      .setControllerAdvice(new GlobalExceptionHandler(errorProperties()))
      .setValidator(validator())
      .build();

  @Test
  void checkout_missing_principal_returns_problem_json_401() throws Exception {
    mockMvc.perform(post("/api/orders/checkout")
            .header("Idempotency-Key", "key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("pm-card-1")))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"));

    assertThat(orderRepository.saveCount()).isZero();
  }

  @Test
  void checkout_missing_idempotency_key_returns_problem_json_400() throws Exception {
    mockMvc.perform(post("/api/orders/checkout")
            .requestAttr("commercePrincipal", principal("alice", "orders:write"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("pm-card-1")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Bad request"))
        .andExpect(jsonPath("$.detail").value("idempotency key is required"))
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));

    assertThat(orderRepository.saveCount()).isZero();
  }

  @Test
  void checkout_returns_created_order_projection() throws Exception {
    mockMvc.perform(post("/api/orders/checkout")
            .requestAttr("commercePrincipal", principal("alice", "orders:write"))
            .header("Idempotency-Key", "key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("pm-card-1")))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value("generated-order"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.totalCents").value(1250))
        .andExpect(jsonPath("$.lines[0].productId").value("starter-mug"));
  }

  @Test
  void checkout_idempotency_conflict_returns_problem_json_409() throws Exception {
    mockMvc.perform(post("/api/orders/checkout")
            .requestAttr("commercePrincipal", principal("alice", "orders:write"))
            .header("Idempotency-Key", "key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("pm-card-1")))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/orders/checkout")
            .requestAttr("commercePrincipal", principal("alice", "orders:write"))
            .header("Idempotency-Key", "key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("pm-card-2")))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void get_order_missing_read_scope_returns_problem_json_403() throws Exception {
    mockMvc.perform(get("/api/orders/alice-order")
            .requestAttr("commercePrincipal", principal("alice", "orders:write")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Forbidden"));
  }

  @Test
  void list_orders_returns_current_users_paginated_order_projections() throws Exception {
    mockMvc.perform(get("/api/orders")
            .param("limit", "1")
            .requestAttr("commercePrincipal", principal("alice", "orders:read")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.items[0].id").value("alice-order"))
        .andExpect(jsonPath("$.items[0].lines[0].productId").value("starter-mug"))
        .andExpect(jsonPath("$.nextCursor").value(CursorPaginator.encodeCursor("alice-order")));
  }

  @Test
  void cancel_order_returns_updated_projection() throws Exception {
    mockMvc.perform(post("/api/orders/alice-order/cancel")
            .requestAttr("commercePrincipal", principal("alice", "orders:write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void get_unknown_order_returns_problem_json_404() throws Exception {
    mockMvc.perform(get("/api/orders/missing-order")
            .requestAttr("commercePrincipal", principal("alice", "orders:read")))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/order/order-not-found"))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.detail").value("order not found: missing-order"))
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
  }

  @Test
  void cancel_unknown_order_returns_problem_json_404() throws Exception {
    mockMvc.perform(post("/api/orders/missing-order/cancel")
            .requestAttr("commercePrincipal", principal("alice", "orders:write")))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/order/order-not-found"))
        .andExpect(jsonPath("$.title").value("Resource not found"))
        .andExpect(jsonPath("$.detail").value("order not found: missing-order"))
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));

    assertThat(orderRepository.saveCount()).isZero();
  }

  /**
   * Cancelling an already-cancelled order is a state conflict, not a server fault. {@code
   * Order.cancel()} throws {@link IllegalStateException} ("order already cancelled") by deliberate
   * domain contract (see OrderDomainTest); the service layer translates that invariant into {@code
   * OrderAlreadyCancelledException extends ConflictException}, so the starter advice renders a 409
   * with the {@code order-already-cancelled} type. The domain stays free of any web-starter
   * dependency.
   */
  @Test
  void cancel_already_cancelled_order_returns_problem_json_409() throws Exception {
    mockMvc.perform(post("/api/orders/alice-cancelled-order/cancel")
            .requestAttr("commercePrincipal", principal("alice", "orders:write")))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/order/order-already-cancelled"))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value("order already cancelled: alice-cancelled-order"))
        .andExpect(jsonPath("$.errorCode").value("ORDER_ALREADY_CANCELLED"));
  }

  @Test
  void checkout_invalid_body_returns_problem_json_400() throws Exception {
    mockMvc.perform(post("/api/orders/checkout")
            .requestAttr("commercePrincipal", principal("alice", "orders:write"))
            .header("Idempotency-Key", "key-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson("")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("https://errors.example.com/order/validation-failed"))
        .andExpect(jsonPath("$.title").value("Invalid request"))
        .andExpect(jsonPath("$.detail").value("request validation failed"))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

    assertThat(orderRepository.saveCount()).isZero();
  }

  private static String checkoutJson(String paymentMethodId) {
    return """
        {
          "paymentMethodId": "%s",
          "shippingPostalCode": "94105"
        }
        """.formatted(paymentMethodId);
  }

  private static CommercePrincipal principal(String subject, String... scopes) {
    return new CommercePrincipal(subject, Set.of(scopes), "fingerprint-" + subject);
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    return validator;
  }

  private static CommerceErrorProperties errorProperties() {
    CommerceErrorProperties properties = new CommerceErrorProperties();
    properties.setBaseUrl("https://errors.example.com/order");
    return properties;
  }

  private static Order aliceOrder() {
    return Order.confirmed(
        new OrderId("alice-order"),
        "alice",
        new CartId("alice-cart"),
        List.of(new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        "auth-alice-order",
        NOW);
  }

  private static Order aliceCancelledOrder() {
    return new Order(
        new OrderId("alice-cancelled-order"),
        "alice",
        new CartId("alice-cart"),
        List.of(new OrderLine(new ProductId("starter-mug"), 1, Money.usd("12.50"))),
        Money.usd("12.50"),
        "auth-alice-cancelled-order",
        NOW,
        OrderStatus.CANCELLED);
  }

  private static final class RecordingOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new LinkedHashMap<>();
    private int saveCount;

    private RecordingOrderRepository(List<Order> initialOrders) {
      initialOrders.forEach(order -> orders.put(order.id(), order.copy()));
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
      return Optional.ofNullable(orders.get(orderId)).map(Order::copy);
    }

    @Override
    public List<Order> findPageByOwnerSub(String ownerSub, @Nullable String afterId, int limit) {
      return orders.values().stream()
          .filter(order -> order.ownerSub().equals(ownerSub))
          .filter(order -> afterId == null || order.id().value().compareTo(afterId) < 0)
          .sorted((left, right) -> right.id().value().compareTo(left.id().value()))
          .limit(limit)
          .map(Order::copy)
          .toList();
    }

    @Override
    public Order save(Order order) {
      saveCount++;
      orders.put(order.id(), order.copy());
      return order.copy();
    }

    int saveCount() {
      return saveCount;
    }
  }

  private static final class RecordingIdempotencyRepository implements IdempotencyRepository {

    private final Map<String, IdempotencyRecord> records = new LinkedHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String subject, IdempotencyKey key) {
      return Optional.ofNullable(records.get(subject + ":" + key.value()));
    }

    @Override
    public boolean claim(String subject, IdempotencyKey key, String requestFingerprint, OrderId reservedOrderId) {
      String mapKey = subject + ":" + key.value();
      if (records.containsKey(mapKey)) {
        return false;
      }
      records.put(mapKey, new IdempotencyRecord(subject, key, requestFingerprint, reservedOrderId));
      return true;
    }

    @Override
    public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
      String mapKey = subject + ":" + key.value();
      IdempotencyRecord existing = records.get(mapKey);
      if (existing == null) {
        throw new IllegalStateException("idempotency record not claimed");
      }
      if (!existing.orderId().equals(orderId)) {
        throw new IllegalStateException("idempotency record linked to a different order");
      }
      records.put(mapKey, new IdempotencyRecord(subject, key, existing.requestFingerprint(), orderId));
    }
  }

  private static final class RecordingPaymentClient implements PaymentClient {

    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
      return new PaymentAuthorization("auth-" + command.orderId().value());
    }
  }

  private static final class AllowingAuthorizationClient implements AuthorizationClient {

    @Override
    public AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission) {
      return AuthorizationDecision.allow(
          DecisionTrace.resource(true, subject, resource, permission, "relationship_found"));
    }

    @Override
    public void writeRelationship(Relationship relationship) {
      // Test fake: order ownership provisioning is allowed in controller tests.
    }
  }
}
