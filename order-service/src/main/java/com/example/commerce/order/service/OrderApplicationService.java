package com.example.commerce.order.service;

import com.example.commerce.order.domain.IdempotencyKey;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.Permission;
import com.example.commerce.security.Relationship;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import com.example.commerce.web.pagination.CursorPaginator;
import com.example.commerce.web.pagination.Page;
import com.example.commerce.web.tsid.TsidGenerator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public final class OrderApplicationService {

  private static final String ORDERS_READ = "orders:read";
  private static final String ORDERS_WRITE = "orders:write";
  private static final Permission CART_READ = new Permission("read");
  private static final Permission ORDER_READ = new Permission("read");
  private static final Permission ORDER_CANCEL = new Permission("cancel");

  private final OrderRepository orderRepository;
  private final CartLookup cartLookup;
  private final IdempotencyRepository idempotencyRepository;
  private final OrderCheckoutPersistence checkoutPersistence;
  private final PaymentClient paymentClient;
  private final ScopeAuthorizer scopeAuthorizer;
  private final ResourceAuthorizer resourceAuthorizer;
  private final CursorPaginator paginator;
  private final Supplier<OrderId> orderIdGenerator;
  private final Clock clock;

  public OrderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      OrderCheckoutPersistence checkoutPersistence,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      CursorPaginator paginator,
      TsidGenerator tsidGenerator) {
    this(
        orderRepository,
        cartLookup,
        idempotencyRepository,
        checkoutPersistence,
        paymentClient,
        scopeAuthorizer,
        resourceAuthorizer,
        paginator,
        // Reserved up front by the recover-forward state machine; TSID is sortable and replaces UUID.
        () -> new OrderId(tsidGenerator.newId()),
        Clock.systemUTC());
  }

  public OrderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      OrderCheckoutPersistence checkoutPersistence,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      CursorPaginator paginator,
      Supplier<OrderId> orderIdGenerator,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.cartLookup = cartLookup;
    this.idempotencyRepository = idempotencyRepository;
    this.checkoutPersistence = checkoutPersistence;
    this.paymentClient = paymentClient;
    this.scopeAuthorizer = scopeAuthorizer;
    this.resourceAuthorizer = resourceAuthorizer;
    this.paginator = paginator;
    this.orderIdGenerator = orderIdGenerator;
    this.clock = clock;
  }

  public OrderResult checkout(
      CommercePrincipal principal, CheckoutCommand command, IdempotencyKey idempotencyKey) {
    List<DecisionTrace> traces = new ArrayList<>();
    traces.add(scopeAuthorizer.requireScope(principal, ORDERS_WRITE));

    CartSnapshot cart = cartLookup.findCurrentCartForSubject(principal.subject())
        .orElseThrow(() -> new OrderNotFoundException("cart not found for current user"));
    traces.add(resourceAuthorizer.requireAllowed(principal, cartResource(cart), CART_READ));

    String requestFingerprint = command.requestFingerprint(cart);
    OrderId orderId = orderIdGenerator.get();
    boolean claimed = idempotencyRepository.claim(
        principal.subject(), idempotencyKey, requestFingerprint, orderId);
    if (!claimed) {
      IdempotencyRecord existing = existingClaim(principal.subject(), idempotencyKey, requestFingerprint);
      return orderRepository.findById(existing.orderId())
          .map(order -> replayCompletedOrder(principal, order, traces))
          .orElseGet(() -> completeClaimedCheckout(
              principal, command, idempotencyKey, existing.orderId(), cart, traces));
    }

    return completeClaimedCheckout(principal, command, idempotencyKey, orderId, cart, traces);
  }

  private OrderResult completeClaimedCheckout(
      CommercePrincipal principal,
      CheckoutCommand command,
      IdempotencyKey idempotencyKey,
      OrderId orderId,
      CartSnapshot cart,
      List<DecisionTrace> traces) {
    PaymentAuthorization authorization = paymentClient.authorize(new PaymentAuthorizationCommand(
        orderId,
        principal.subject(),
        cart.id(),
        cart.total(),
        idempotencyKey,
        command.paymentMethodId()));
    Order order = Order.confirmed(
        orderId,
        principal.subject(),
        cart.id(),
        cart.lines(),
        cart.total(),
        authorization.authorizationId(),
        clock.instant());
    Order saved = checkoutPersistence.persistAndLink(principal.subject(), idempotencyKey, order);
    ensureOrderOwner(principal, orderId, traces);
    return new OrderResult(saved, List.copyOf(traces));
  }

  public OrderResult getOrder(CommercePrincipal principal, OrderId orderId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, ORDERS_READ);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, orderResource(orderId), ORDER_READ);
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("order not found: " + orderId.value()));
    return new OrderResult(order, List.of(scopeTrace, resourceTrace));
  }

  public Page<OrderResult> listOrders(
      CommercePrincipal principal, @Nullable Integer limit, @Nullable String cursor) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, ORDERS_READ);
    int pageSize = paginator.resolveLimit(limit);
    String afterId = CursorPaginator.decodeCursor(cursor);
    List<Order> rawRows = orderRepository.findPageByOwnerSub(principal.subject(), afterId, pageSize + 1);
    boolean hasMore = rawRows.size() > pageSize;
    List<OrderResult> items = (hasMore ? rawRows.subList(0, pageSize) : rawRows)
        .stream()
        .flatMap(order -> resourceAuthorizer
            .filterAllowed(principal, orderResource(order.id()), ORDER_READ)
            .map(resourceTrace -> new OrderResult(order, List.of(scopeTrace, resourceTrace)))
            .stream())
        .toList();
    String nextCursor = hasMore && !items.isEmpty()
        ? CursorPaginator.encodeCursor(items.get(items.size() - 1).order().id().value())
        : null;
    return new Page<>(items, nextCursor);
  }

  public OrderResult cancelOrder(CommercePrincipal principal, OrderId orderId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, ORDERS_WRITE);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, orderResource(orderId), ORDER_CANCEL);
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("order not found: " + orderId.value()));
    try {
      order.cancel();
    } catch (IllegalStateException e) {
      throw new OrderAlreadyCancelledException("order already cancelled: " + orderId.value(), e);
    }
    orderRepository.save(order);
    return new OrderResult(order, List.of(scopeTrace, resourceTrace));
  }

  private static ResourceRef cartResource(CartSnapshot cart) {
    return new ResourceRef("cart", cart.id().value());
  }

  private static ResourceRef orderResource(OrderId orderId) {
    return new ResourceRef("order", orderId.value());
  }

  private IdempotencyRecord existingClaim(
      String subject, IdempotencyKey idempotencyKey, String requestFingerprint) {
    IdempotencyRecord existing = idempotencyRepository.find(subject, idempotencyKey)
        .orElseThrow(() -> new IllegalStateException("idempotency claim disappeared"));
    if (!existing.requestFingerprint().equals(requestFingerprint)) {
      throw new IdempotencyConflictException("idempotency key was already used for a different request");
    }
    return existing;
  }

  private OrderResult replayCompletedOrder(
      CommercePrincipal principal, Order existingOrder, List<DecisionTrace> traces) {
    ensureOrderOwner(principal, existingOrder.id(), traces);
    return new OrderResult(existingOrder, List.copyOf(traces));
  }

  private void ensureOrderOwner(CommercePrincipal principal, OrderId orderId, List<DecisionTrace> traces) {
    traces.add(resourceAuthorizer.writeRelationship(
        new Relationship(orderResource(orderId), "owner", SubjectRef.user(principal.subject()))));
  }
}
