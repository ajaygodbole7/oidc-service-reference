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
import java.util.Optional;
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
    // SpiceDB owner relationship is written BEFORE the Postgres commit so a SpiceDB outage
    // between the two calls cannot leave a charged, committed order permanently orphaned.
    // A SpiceDB failure here throws and the @Transactional in persistAndLink never commits.
    ensureOrderOwner(principal, orderId, traces);
    Order saved = checkoutPersistence.persistAndLink(principal.subject(), idempotencyKey, order);
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
    String scanAfterId = CursorPaginator.decodeCursor(cursor);
    // owner_sub narrows the current-user history cursor window so cursors never expose
    // unrelated order ids. SpiceDB remains the read authority for every candidate row.
    // MAX_SCAN_BATCHES caps DB round-trips per request: a bulk SpiceDB revocation (all rows denied)
    // would otherwise turn one API call into O(N/pageSize) queries. At the cap we return whatever
    // was collected (possibly < pageSize items) with no cursor — fail-closed, not fail-open.
    final int MAX_SCAN_BATCHES = 10;
    List<OrderResult> items = new ArrayList<>();
    boolean hasMoreAllowed = false;
    boolean exhausted = false;
    for (int batch = 0; batch < MAX_SCAN_BATCHES && !hasMoreAllowed && !exhausted; batch++) {
      List<Order> candidates = orderRepository.findPageByOwnerSub(principal.subject(), scanAfterId, pageSize + 1);
      if (candidates.isEmpty()) {
        exhausted = true;
        break;
      }
      List<ResourceRef> orderResources = candidates.stream().map(o -> orderResource(o.id())).toList();
      List<Optional<DecisionTrace>> decisions =
          resourceAuthorizer.filterAllowed(principal, orderResources, ORDER_READ);
      for (int i = 0; i < candidates.size(); i++) {
        Order order = candidates.get(i);
        Optional<DecisionTrace> decision = decisions.get(i);
        if (decision.isEmpty()) {
          continue;
        }
        if (items.size() < pageSize) {
          items.add(new OrderResult(order, List.of(scopeTrace, decision.get())));
        } else {
          hasMoreAllowed = true;
          break;
        }
      }
      if (!hasMoreAllowed) {
        scanAfterId = candidates.get(candidates.size() - 1).id().value();
        exhausted = candidates.size() <= pageSize;
      }
    }
    // nextCursor is null ONLY at true exhaustion. Three exit cases:
    //  - hasMoreAllowed: a confirmed (pageSize+1)th allowed item exists → resume after the last
    //    returned item's id (no denied id is ever encoded).
    //  - cap hit (!exhausted): the scan stopped with same-owner rows still unscanned → resume from
    //    the last SCANNED row so allowed orders beyond a denied block larger than the scan window
    //    stay reachable across requests. scanAfterId is the owner's own row (the query is
    //    owner_sub-narrowed), so the cursor never encodes another subject's id.
    //  - exhausted: genuinely no more rows for this owner → null.
    String nextCursor;
    if (hasMoreAllowed) {
      nextCursor = CursorPaginator.encodeCursor(items.get(items.size() - 1).order().id().value());
    } else if (!exhausted) {
      nextCursor = CursorPaginator.encodeCursor(scanAfterId);
    } else {
      nextCursor = null;
    }
    return new Page<>(List.copyOf(items), nextCursor);
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
