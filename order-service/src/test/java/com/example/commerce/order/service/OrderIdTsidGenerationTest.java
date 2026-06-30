package com.example.commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.ProductId;
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
import com.example.commerce.web.pagination.CursorPaginator;
import com.example.commerce.web.tsid.TsidGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The production constructor must reserve the new order id from the injected {@link TsidGenerator}
 * (replacing the old {@code UUID.randomUUID()} default), and the reserved id must flow unchanged
 * through the reserve-then-claim checkout into the persisted order and the payment command.
 */
class OrderIdTsidGenerationTest {

  @Test
  void checkout_reserves_order_id_from_the_tsid_generator() {
    TsidGenerator tsid = () -> "6801HWW000ABC";
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    InMemoryIdempotencyRepository idempotencyRepository = new InMemoryIdempotencyRepository();
    OrderApplicationService service = new OrderApplicationService(
        orderRepository,
        aliceCartLookup(),
        idempotencyRepository,
        new OrderCheckoutPersistence(orderRepository, idempotencyRepository),
        paymentClient,
        new ScopeAuthorizer(),
        new ResourceAuthorizer(new AllowingAuthorizationClient()),
        new CursorPaginator(20, 100),
        tsid);

    OrderResult result = service.checkout(
        principal("alice", "orders:write"),
        new CheckoutCommand("pm-card-1", "94105"),
        new IdempotencyKey("key-1"));

    OrderId expected = new OrderId("6801HWW000ABC");
    assertThat(result.order().id()).isEqualTo(expected);
    assertThat(paymentClient.lastOrderId).isEqualTo(expected);
    assertThat(orderRepository.findById(expected)).isPresent();
  }

  @Test
  void each_checkout_uses_a_fresh_id_from_the_generator() {
    java.util.ArrayDeque<String> ids = new java.util.ArrayDeque<>(List.of("6801HWW000AAA", "6801HWW000BBB"));
    TsidGenerator tsid = ids::poll;
    InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    InMemoryIdempotencyRepository idempotencyRepository = new InMemoryIdempotencyRepository();
    OrderApplicationService service = new OrderApplicationService(
        orderRepository,
        aliceCartLookup(),
        idempotencyRepository,
        new OrderCheckoutPersistence(orderRepository, idempotencyRepository),
        new RecordingPaymentClient(),
        new ScopeAuthorizer(),
        new ResourceAuthorizer(new AllowingAuthorizationClient()),
        new CursorPaginator(20, 100),
        tsid);

    OrderResult first = service.checkout(
        principal("alice", "orders:write"), new CheckoutCommand("pm-card-1", "94105"), new IdempotencyKey("key-1"));
    OrderResult second = service.checkout(
        principal("alice", "orders:write"), new CheckoutCommand("pm-card-1", "94105"), new IdempotencyKey("key-2"));

    assertThat(first.order().id()).isEqualTo(new OrderId("6801HWW000AAA"));
    assertThat(second.order().id()).isEqualTo(new OrderId("6801HWW000BBB"));
  }

  private static CartLookup aliceCartLookup() {
    return subject -> Optional.of(new CartSnapshot(new CartId("alice-cart"), List.of(
        new OrderLine(new ProductId("6801HWW000000"), 1, Money.usd("12.50")))));
  }

  private static CommercePrincipal principal(String subject, String... scopes) {
    return new CommercePrincipal(subject, Set.of(scopes), "fingerprint-" + subject);
  }

  private static final class RecordingPaymentClient implements PaymentClient {

    private OrderId lastOrderId;

    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
      lastOrderId = command.orderId();
      return new PaymentAuthorization("auth-" + command.orderId().value());
    }
  }

  private static final class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> orders = new LinkedHashMap<>();

    @Override
    public Optional<Order> findById(OrderId orderId) {
      return Optional.ofNullable(orders.get(orderId)).map(Order::copy);
    }

    @Override
    public List<Order> findPage(@Nullable String afterId, int limit) {
      return orders.values().stream()
          .filter(order -> afterId == null || order.id().value().compareTo(afterId) < 0)
          .sorted((left, right) -> right.id().value().compareTo(left.id().value()))
          .limit(limit)
          .map(Order::copy)
          .toList();
    }

    @Override
    public Order save(Order order) {
      orders.put(order.id(), order.copy());
      return order.copy();
    }
  }

  private static final class InMemoryIdempotencyRepository implements IdempotencyRepository {

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
      records.put(mapKey, new IdempotencyRecord(subject, key, existing.requestFingerprint(), orderId));
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
      // Test fake: order ownership provisioning is allowed.
    }
  }
}
