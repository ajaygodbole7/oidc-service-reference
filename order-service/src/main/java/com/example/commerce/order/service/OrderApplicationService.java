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
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class OrderApplicationService {

  private static final String ORDERS_READ = "orders:read";
  private static final String ORDERS_WRITE = "orders:write";
  private static final Permission CART_READ = new Permission("read");
  private static final Permission ORDER_READ = new Permission("read");
  private static final Permission ORDER_CANCEL = new Permission("cancel");

  private final OrderRepository orderRepository;
  private final CartLookup cartLookup;
  private final IdempotencyRepository idempotencyRepository;
  private final PaymentClient paymentClient;
  private final ScopeAuthorizer scopeAuthorizer;
  private final ResourceAuthorizer resourceAuthorizer;
  private final Supplier<OrderId> orderIdGenerator;
  private final Clock clock;

  public OrderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    this(
        orderRepository,
        cartLookup,
        idempotencyRepository,
        paymentClient,
        scopeAuthorizer,
        resourceAuthorizer,
        () -> new OrderId(UUID.randomUUID().toString()),
        Clock.systemUTC());
  }

  public OrderApplicationService(
      OrderRepository orderRepository,
      CartLookup cartLookup,
      IdempotencyRepository idempotencyRepository,
      PaymentClient paymentClient,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer,
      Supplier<OrderId> orderIdGenerator,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.cartLookup = cartLookup;
    this.idempotencyRepository = idempotencyRepository;
    this.paymentClient = paymentClient;
    this.scopeAuthorizer = scopeAuthorizer;
    this.resourceAuthorizer = resourceAuthorizer;
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
    IdempotencyRecord existing = idempotencyRepository.find(principal.subject(), idempotencyKey)
        .orElse(null);
    if (existing != null) {
      if (!existing.requestFingerprint().equals(requestFingerprint)) {
        throw new IdempotencyConflictException("idempotency key was already used for a different request");
      }
      Order existingOrder = orderRepository.findById(existing.orderId())
          .orElseThrow(() -> new OrderNotFoundException("idempotent order not found: " + existing.orderId().value()));
      return new OrderResult(existingOrder, List.copyOf(traces));
    }

    OrderId orderId = orderIdGenerator.get();
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
    traces.add(resourceAuthorizer.writeRelationship(
        new Relationship(orderResource(orderId), "owner", SubjectRef.user(principal.subject()))));
    orderRepository.save(order);
    idempotencyRepository.save(new IdempotencyRecord(
        principal.subject(), idempotencyKey, requestFingerprint, order.id()));
    return new OrderResult(order, List.copyOf(traces));
  }

  public OrderResult getOrder(CommercePrincipal principal, OrderId orderId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, ORDERS_READ);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, orderResource(orderId), ORDER_READ);
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("order not found: " + orderId.value()));
    return new OrderResult(order, List.of(scopeTrace, resourceTrace));
  }

  public OrderResult cancelOrder(CommercePrincipal principal, OrderId orderId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, ORDERS_WRITE);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, orderResource(orderId), ORDER_CANCEL);
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException("order not found: " + orderId.value()));
    order.cancel();
    orderRepository.save(order);
    return new OrderResult(order, List.of(scopeTrace, resourceTrace));
  }

  private static ResourceRef cartResource(CartSnapshot cart) {
    return new ResourceRef("cart", cart.id().value());
  }

  private static ResourceRef orderResource(OrderId orderId) {
    return new ResourceRef("order", orderId.value());
  }
}
