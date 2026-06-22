# Domain modeling

Each service uses the same layering. `scripts/verify-architecture.sh` enforces the boundaries;
this document explains them.

## Layers

| Layer | Package | Responsibility |
| --- | --- | --- |
| Web | `..web` | HTTP adapter. Thin controllers: map the request, call the application service, map the result. |
| Application service | `..service` | Orchestrates a use case. Runs the scope and resource gates, drives the domain and repositories. |
| Domain | `..domain` | Business types and invariants. Pure Java, no framework or other-layer imports. |
| Persistence | `..persistence` | Repository implementations behind domain interfaces (Spring Data JDBC). |
| Config | `..config` | Wiring. Builds the beans and injects validators, repositories, and authorizers. |

Dependencies point inward: web depends on service, service on the domain and the repository
interfaces, persistence implements those interfaces. The domain depends on nothing else.

The cross-cutting web concerns do not live in any one service. `commerce-web-starter`, a Spring
Boot auto-configured starter, supplies them to all four: the RFC 9457 error handling
(`GlobalExceptionHandler`, the sealed `ApiException` hierarchy, `ProblemDetailFactory`), the
`TsidGenerator` for ids, the keyset `CursorPaginator`/`Page`, and `TraceIdFilter`. Each bean is
`@ConditionalOnMissingBean`, so a service can override one without forking the rest.

## Controllers are web adapters

A controller receives the validated `CommercePrincipal` (set as a request attribute after gate 2
by `CommercePrincipalFilter`), maps the request to a command, calls one application-service
method, and maps the result. From `CartController`:

```java
@PostMapping("/api/cart/items")
@ResponseStatus(HttpStatus.CREATED)
CartResponse addItem(
    @RequestAttribute("commercePrincipal") CommercePrincipal principal,
    @Valid @RequestBody CartRequest.AddItem request) {
  AddItemCommand command = new AddItemCommand(
      new ProductId(request.productId()), request.quantityValue(), request.unitPriceValue());
  return CartResponse.from(service.addItem(principal, command));
}
```

No business logic, no persistence, no authorizers. The architecture gate fails the build if a
controller imports `..persistence`, `ScopeAuthorizer`, or `ResourceAuthorizer`.

## Application services orchestrate

The use case lives in the application service. `CartApplicationService.addItem`:

```java
public CartResult addItem(CommercePrincipal principal, AddItemCommand command) {
  scopeAuthorizer.requireScope(principal, CART_WRITE);                       // gate 3
  Cart cart = repository.findByOwnerSub(principal.subject()).orElse(null);
  if (cart == null) {
    // first add: server-generated id, provision cart:{id}#owner, persist
  }
  resourceAuthorizer.requireAllowed(principal, resource(cart.id()), WRITE);  // gate 4
  // apply the change to the domain object, then persist
  repository.save(cart);
  // ...
}
```

The order is deliberate: the cheap scope check before the SpiceDB round trip, and authorization
before any state change. Checkout adds the server-to-server payment call between authorization
and persistence.

## Domain objects enforce invariants

Domain types (`Cart`, `Order`, `Money`, the id value types) hold business state and rules. They
carry the owner as a plain subject string, never a token, principal, or framework type. An
`Order` validates its own construction (lines, total, status); the application service does not
reach past those rules to mutate fields.

## Persistence stays behind interfaces

The domain defines a repository interface (for example `OrderRepository`); the persistence layer
provides a Spring Data JDBC implementation (`PostgresOrderRepository`) using a separate row type
so the domain stays free of mapping annotations. Swapping the store means a new implementation,
not a change to the domain or services. Ownership columns are business data; they never decide
access. Gate 4 does.

## Identifiers are server-minted TSIDs

Every storage id is a 13-char Crockford base32 TSID minted by the server via `TsidGenerator`
(default `HypersistenceTsidGenerator`); id columns are `VARCHAR`. TSIDs are fixed-width and
time-sortable, so `ORDER BY id` and `WHERE id > :cursor` are a stable keyset, which is what the
cursor pagination rests on. Each id is a value type that wraps a `String` (`OrderId`, `CartId`,
`ProductId`, `StoreId`), keeping the raw string off the domain method signatures.

## Aggregate concurrency uses an optimistic version

The cart, order, and payment `*Row` types carry a `@Version` column. Each aggregate handles
concurrency differently:

- **Cart** threads the version through the domain: a loaded `Cart` carries the persisted version,
  and the update asserts on it, so a concurrent change fails the lock with
  `OptimisticLockingFailureException`.
- **Order** uses reload-converge: the domain does not carry a version; `save` re-reads the stored
  version at write time and writes onto it. This is the recover-forward idempotency path (checkout
  writes the order once; cancel is idempotent), not a conflict guard. The `version` column is still
  bumped, available should a future concurrent-mutation path need it.
- **Payment** is insert-only: an authorization is written exactly once, so the version starts null
  and is never updated; it is present for a future update path. Replays lose the
  `idempotency_key` unique-constraint race instead.

A failed lock surfaces as `OptimisticLockingFailureException`, which the shared
`GlobalExceptionHandler` maps to an RFC 9457 409 (`concurrent-modification`).

## Security helpers do not leak into the domain

Application services call `ScopeAuthorizer` and `ResourceAuthorizer` from
`commerce-security-common`. SpiceDB sits behind the `AuthorizationClient` interface, with a fake
for tests. Raw tokens never enter domain objects: the service receives a `CommercePrincipal`
(subject, scopes, a token fingerprint) and passes the domain only what it needs, which is the
subject.
