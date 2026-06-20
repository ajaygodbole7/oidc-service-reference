package com.example.commerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.OrderStatus;
import com.example.commerce.order.domain.ProductId;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.AuthorizationDeniedException;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InMemoryAuthorizationClient;
import com.example.commerce.security.Permission;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderApplicationServiceTest {

  private static final ResourceRef ALICE_CART = new ResourceRef("cart", "alice-cart");
  private static final ResourceRef ALICE_ORDER = new ResourceRef("order", "alice-order");
  private static final ResourceRef GENERATED_ORDER = new ResourceRef("order", "generated-order");
  private static final Permission READ = new Permission("read");
  private static final Permission CANCEL = new Permission("cancel");
  private static final Instant NOW = Instant.parse("2026-06-20T00:00:00Z");

  @Test
  void checkout_without_write_scope_denies_before_cart_lookup_resource_check_or_payment() {
    RecordingCartLookup cartLookup = cartLookup();
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartRead();
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    RecordingOrderRepository orderRepository = new RecordingOrderRepository(List.of());
    OrderApplicationService service = service(orderRepository, cartLookup, authorizationClient, paymentClient);

    assertThatThrownBy(() -> service.checkout(
            principal("alice", "orders:read"),
            checkoutCommand(),
            new IdempotencyKey("key-1")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope orders:write");

    assertThat(cartLookup.requests()).isEmpty();
    assertThat(authorizationClient.requests()).isEmpty();
    assertThat(paymentClient.commands()).isEmpty();
    assertThat(orderRepository.saveCount()).isZero();
  }

  @Test
  void checkout_resolves_current_user_cart_server_side_and_checks_cart_read_before_payment() {
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartRead();
    RecordingIdempotencyRepository idempotencyRepository = new RecordingIdempotencyRepository();
    RecordingPaymentClient paymentClient = new RecordingPaymentClient(idempotencyRepository);
    RecordingOrderRepository orderRepository = new RecordingOrderRepository(List.of());
    OrderApplicationService service = service(
        orderRepository, cartLookup(), idempotencyRepository, authorizationClient, paymentClient);

    OrderResult result = service.checkout(
        principal("alice", "orders:write"),
        checkoutCommand(),
        new IdempotencyKey("key-1"));

    assertThat(result.order().id()).isEqualTo(new OrderId("generated-order"));
    assertThat(result.order().sourceCartId()).isEqualTo(new CartId("alice-cart"));
    assertThat(paymentClient.commands())
        .extracting(PaymentAuthorizationCommand::cartId)
        .containsExactly(new CartId("alice-cart"));
    assertThat(paymentClient.idempotencyWasClaimedBeforePayment()).isTrue();
    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:alice-cart", "read"));
    assertThat(authorizationClient.relationshipWrites())
        .containsExactly(new Relationship(GENERATED_ORDER, "owner", SubjectRef.user("alice")));
  }

  @Test
  void checkout_claims_idempotency_before_payment() {
    RecordingIdempotencyRepository idempotencyRepository = new RecordingIdempotencyRepository();
    RecordingPaymentClient paymentClient = new RecordingPaymentClient(idempotencyRepository);
    OrderApplicationService service = service(
        new RecordingOrderRepository(List.of()),
        cartLookup(),
        idempotencyRepository,
        authorizationClientWithAliceCartRead(),
        paymentClient);

    service.checkout(principal("alice", "orders:write"), checkoutCommand(), new IdempotencyKey("key-1"));

    assertThat(idempotencyRepository.claimCount()).isEqualTo(1);
    assertThat(paymentClient.idempotencyWasClaimedBeforePayment()).isTrue();
  }

  @Test
  void checkout_does_not_call_payment_when_idempotency_claim_fails() {
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    OrderApplicationService service = service(
        new RecordingOrderRepository(List.of()),
        cartLookup(),
        new FailingIdempotencyRepository(),
        authorizationClientWithAliceCartRead(),
        paymentClient);

    assertThatThrownBy(() -> service.checkout(
            principal("alice", "orders:write"),
            checkoutCommand(),
            new IdempotencyKey("key-1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("idempotency unavailable");

    assertThat(paymentClient.commands()).isEmpty();
  }

  @Test
  void checkout_with_denied_cart_relationship_does_not_call_payment_or_save() {
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    RecordingOrderRepository orderRepository = new RecordingOrderRepository(List.of());
    OrderApplicationService service = service(orderRepository, cartLookup(), authorizationClient, paymentClient);

    assertThatThrownBy(() -> service.checkout(
            principal("alice", "orders:write"),
            checkoutCommand(),
            new IdempotencyKey("key-1")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");

    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:alice-cart", "read"));
    assertThat(paymentClient.commands()).isEmpty();
    assertThat(orderRepository.saveCount()).isZero();
  }

  @Test
  void idempotent_replay_returns_same_order_without_double_authorizing_payment() {
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    OrderApplicationService service = service(
        new RecordingOrderRepository(List.of()),
        cartLookup(),
        authorizationClientWithAliceCartRead(),
        paymentClient);
    CommercePrincipal principal = principal("alice", "orders:write");
    IdempotencyKey key = new IdempotencyKey("key-1");

    OrderResult first = service.checkout(principal, checkoutCommand(), key);
    OrderResult replay = service.checkout(principal, checkoutCommand(), key);

    assertThat(replay.order().id()).isEqualTo(first.order().id());
    assertThat(paymentClient.commands()).hasSize(1);
  }

  @Test
  void idempotency_collision_rejects_before_second_payment_call() {
    RecordingPaymentClient paymentClient = new RecordingPaymentClient();
    OrderApplicationService service = service(
        new RecordingOrderRepository(List.of()),
        cartLookup(),
        authorizationClientWithAliceCartRead(),
        paymentClient);
    CommercePrincipal principal = principal("alice", "orders:write");
    IdempotencyKey key = new IdempotencyKey("key-1");

    service.checkout(principal, checkoutCommand(), key);

    assertThatThrownBy(() -> service.checkout(
            principal,
            new CheckoutCommand("pm-card-2", "94105"),
            key))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessageContaining("different request");
    assertThat(paymentClient.commands()).hasSize(1);
  }

  @Test
  void read_requires_read_scope_before_resource_check() {
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceOrderRead();
    OrderApplicationService service = service(
        new RecordingOrderRepository(List.of(aliceOrder())),
        cartLookup(),
        authorizationClient,
        new RecordingPaymentClient());

    assertThatThrownBy(() -> service.getOrder(principal("alice", "orders:write"), new OrderId("alice-order")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope orders:read");

    assertThat(authorizationClient.requests()).isEmpty();
  }

  @Test
  void support_can_read_when_spicedb_grants_read_but_cannot_cancel_without_cancel_relationship() {
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("support"), ALICE_ORDER, READ));
    RecordingOrderRepository orderRepository = new RecordingOrderRepository(List.of(aliceOrder()));
    OrderApplicationService service = service(
        orderRepository,
        cartLookup(),
        authorizationClient,
        new RecordingPaymentClient());

    OrderResult read = service.getOrder(principal("support", "orders:read"), new OrderId("alice-order"));

    assertThat(read.order().id()).isEqualTo(new OrderId("alice-order"));
    assertThatThrownBy(() -> service.cancelOrder(principal("support", "orders:write"), new OrderId("alice-order")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");
    assertThat(orderRepository.findById(new OrderId("alice-order")).orElseThrow().status())
        .isEqualTo(OrderStatus.CONFIRMED);
    assertThat(authorizationClient.requests())
        .containsExactly(
            new CheckRequest("user:support", "order:alice-order", "read"),
            new CheckRequest("user:support", "order:alice-order", "cancel"));
  }

  @Test
  void owner_cancel_requires_write_scope_and_order_cancel_relationship() {
    RecordingOrderRepository orderRepository = new RecordingOrderRepository(List.of(aliceOrder()));
    OrderApplicationService service = service(
        orderRepository,
        cartLookup(),
        authorizationClientWithAliceOrderReadCancel(),
        new RecordingPaymentClient());

    OrderResult result = service.cancelOrder(principal("alice", "orders:write"), new OrderId("alice-order"));

    assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(orderRepository.findById(new OrderId("alice-order")).orElseThrow().status())
        .isEqualTo(OrderStatus.CANCELLED);
  }

  private static OrderApplicationService service(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      AuthorizationClient authorizationClient,
      PaymentClient paymentClient) {
    return service(
        orderRepository,
        cartLookup,
        new RecordingIdempotencyRepository(),
        authorizationClient,
        paymentClient);
  }

  private static OrderApplicationService service(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      AuthorizationClient authorizationClient,
      PaymentClient paymentClient) {
    return new OrderApplicationService(
        orderRepository,
        cartLookup,
        idempotencyRepository,
        new OrderCheckoutPersistence(orderRepository, idempotencyRepository),
        paymentClient,
        new ScopeAuthorizer(),
        new ResourceAuthorizer(authorizationClient),
        () -> new OrderId("generated-order"),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static CheckoutCommand checkoutCommand() {
    return new CheckoutCommand("pm-card-1", "94105");
  }

  private static CommercePrincipal principal(String subject, String... scopes) {
    return new CommercePrincipal(subject, Set.of(scopes), "fingerprint-" + subject);
  }

  private static RecordingCartLookup cartLookup() {
    return new RecordingCartLookup(Map.of(
        "alice", new CartSnapshot(new CartId("alice-cart"), List.of(
            new OrderLine(new ProductId("starter-mug"), 2, Money.usd("12.50"))))));
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

  private static RecordingAuthorizationClient authorizationClientWithAliceCartRead() {
    return recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("alice"), ALICE_CART, READ));
  }

  private static RecordingAuthorizationClient authorizationClientWithAliceOrderRead() {
    return recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("alice"), ALICE_ORDER, READ));
  }

  private static RecordingAuthorizationClient authorizationClientWithAliceOrderReadCancel() {
    return recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("alice"), ALICE_ORDER, READ)
        .grant(SubjectRef.user("alice"), ALICE_ORDER, CANCEL));
  }

  private static RecordingAuthorizationClient recordingClient(AuthorizationClient delegate) {
    return new RecordingAuthorizationClient(delegate);
  }

  private record CheckRequest(String subject, String resource, String permission) {
  }

  private static final class RecordingAuthorizationClient implements AuthorizationClient {

    private final AuthorizationClient delegate;
    private final List<CheckRequest> requests = new ArrayList<>();
    private final List<Relationship> relationshipWrites = new ArrayList<>();

    private RecordingAuthorizationClient(AuthorizationClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission) {
      requests.add(new CheckRequest(subject.toString(), resource.toString(), permission.value()));
      return delegate.check(subject, resource, permission);
    }

    @Override
    public void writeRelationship(Relationship relationship) {
      relationshipWrites.add(relationship);
      delegate.writeRelationship(relationship);
    }

    @Override
    public void deleteRelationship(Relationship relationship) {
      delegate.deleteRelationship(relationship);
    }

    List<CheckRequest> requests() {
      return List.copyOf(requests);
    }

    List<Relationship> relationshipWrites() {
      return List.copyOf(relationshipWrites);
    }
  }

  private static final class RecordingCartLookup implements CartLookup {

    private final Map<String, CartSnapshot> cartsBySubject;
    private final List<String> requests = new ArrayList<>();

    private RecordingCartLookup(Map<String, CartSnapshot> cartsBySubject) {
      this.cartsBySubject = Map.copyOf(cartsBySubject);
    }

    @Override
    public Optional<CartSnapshot> findCurrentCartForSubject(String subject) {
      requests.add(subject);
      return Optional.ofNullable(cartsBySubject.get(subject));
    }

    List<String> requests() {
      return List.copyOf(requests);
    }
  }

  private static final class RecordingIdempotencyRepository implements IdempotencyRepository {

    private final Map<String, IdempotencyRecord> records = new LinkedHashMap<>();
    private int claimCount;
    private int linkCount;

    @Override
    public Optional<IdempotencyRecord> find(String subject, IdempotencyKey key) {
      return Optional.ofNullable(records.get(subject + ":" + key.value()));
    }

    @Override
    public boolean claim(String subject, IdempotencyKey key, String requestFingerprint) {
      String mapKey = subject + ":" + key.value();
      if (records.containsKey(mapKey)) {
        return false;
      }
      claimCount++;
      records.put(mapKey, new IdempotencyRecord(subject, key, requestFingerprint, null));
      return true;
    }

    @Override
    public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
      String mapKey = subject + ":" + key.value();
      IdempotencyRecord existing = records.get(mapKey);
      if (existing == null) {
        throw new IllegalStateException("idempotency record not claimed");
      }
      linkCount++;
      records.put(mapKey, new IdempotencyRecord(subject, key, existing.requestFingerprint(), orderId));
    }

    int claimCount() {
      return claimCount;
    }

    int linkCount() {
      return linkCount;
    }
  }

  private static final class FailingIdempotencyRepository implements IdempotencyRepository {

    @Override
    public Optional<IdempotencyRecord> find(String subject, IdempotencyKey key) {
      return Optional.empty();
    }

    @Override
    public boolean claim(String subject, IdempotencyKey key, String requestFingerprint) {
      throw new IllegalStateException("idempotency unavailable");
    }

    @Override
    public void linkOrder(String subject, IdempotencyKey key, OrderId orderId) {
      throw new IllegalStateException("idempotency unavailable");
    }
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
    public Order save(Order order) {
      saveCount++;
      orders.put(order.id(), order.copy());
      return order.copy();
    }

    int saveCount() {
      return saveCount;
    }
  }

  private static final class RecordingPaymentClient implements PaymentClient {

    private final List<PaymentAuthorizationCommand> commands = new ArrayList<>();
    private final RecordingIdempotencyRepository idempotencyRepository;
    private boolean idempotencyWasClaimedBeforePayment;

    private RecordingPaymentClient() {
      this(null);
    }

    private RecordingPaymentClient(RecordingIdempotencyRepository idempotencyRepository) {
      this.idempotencyRepository = idempotencyRepository;
    }

    @Override
    public PaymentAuthorization authorize(PaymentAuthorizationCommand command) {
      commands.add(command);
      if (idempotencyRepository != null) {
        idempotencyWasClaimedBeforePayment = idempotencyRepository.claimCount() > 0;
      }
      return new PaymentAuthorization("auth-" + command.orderId().value());
    }

    List<PaymentAuthorizationCommand> commands() {
      return List.copyOf(commands);
    }

    boolean idempotencyWasClaimedBeforePayment() {
      return idempotencyWasClaimedBeforePayment;
    }
  }
}
