# Authorization model

Every `/api/**` request clears four independent gates before business logic. Each denies by
default. No gate can be skipped, and no gate substitutes for another.

## The four gates

1. **Gateway session (APISIX).** The opaque `__Host` session cookie is resolved to a
   server-side session and the access token is injected. A request with no valid session gets no
   bearer.
2. **Service JWT (Nimbus).** The service validates the injected JWT: signature against the IdP
   JWKS, `iss`, `exp`, and `aud=commerce-api`. A focused validator is used, not a framework
   resource server, so the accepted token shape is explicit.
3. **Coarse scope.** The application service requires the operation's scope
   (`cart:read`/`cart:write`, `catalog:write`, `orders:read`/`orders:write`). Scopes come from
   the token and answer whether the caller may perform this kind of operation at all.
4. **Fine resource (SpiceDB).** The application service asks SpiceDB whether this subject may
   take this action on this specific resource, read at `fully_consistent`. This answers whether
   the caller may act on this particular object.

Scope and relationship are orthogonal. A `cart:write` token without a `cart:{id}#owner`
relationship is denied at gate 4; an owner relationship without `cart:write` is denied at gate 3.
That pair is `SEC-SCOPE-WITHOUT-RELATIONSHIP` and `SEC-RELATIONSHIP-WITHOUT-SCOPE` in the live
suite.

## SpiceDB schema

Gate 4 uses a small Zanzibar-style schema (`authorization-service/schema.zed`):

```
definition user {}

definition store {
  relation manager: user
  permission view   = manager
  permission manage = manager
}

definition cart {
  relation owner: user
  permission read  = owner
  permission write = owner
}

definition order {
  relation owner: user
  relation support: user
  permission read   = owner + support
  permission cancel = owner
}
```

- **store** gates catalog writes: a merchant who is `store:main#manager` may `manage`.
- **cart** is owner-only: only the owner reads or writes a cart.
- **order** separates duties: `read = owner + support`, but `cancel = owner`. Assigned support
  staff can read an order without being able to cancel it.

## Scope and relationship by operation

| Operation | Scope (gate 3) | SpiceDB check (gate 4) |
| --- | --- | --- |
| Read cart | `cart:read` | `cart:{id}#read` |
| Modify cart | `cart:write` | `cart:{id}#write` |
| Browse catalog | none (anonymous) | none |
| Write catalog | `catalog:write` | `store:main#manage` |
| Read order | `orders:read` | `order:{id}#read` |
| Checkout | `orders:write` | the new order is provisioned to the caller |
| Cancel order | `orders:write` | `order:{id}#cancel` |

## Dynamic ownership provisioning

A cart is created on first add. The service generates the cart id server-side (the client never
chooses it), writes `cart:{id}#owner@user:{subject}` through `ResourceAuthorizer`, then persists.
Later reads and writes use the normal gate-4 check. If the relationship write fails, the
operation fails closed and no cart is returned. These are
`SEC-OWNERSHIP-PROVISIONED-FOR-CALLER`, `SEC-NO-RESOURCE-HIJACK`, and
`SEC-PROVISIONING-FAILS-CLOSED`.

## Fail-closed

- SpiceDB unavailable denies. `ResourceAuthorizer` turns an `AuthorizationUnavailableException`
  into a denial, never an allow (`SEC-SPICEDB-UNAVAILABLE`).
- Removing a relationship denies the next request immediately, because gate 4 reads
  `fully_consistent` (`SEC-RELATIONSHIP-REMOVAL-IMMEDIATE`).
- Ownership columns in Postgres are never consulted for access; gate 4 is the only resource
  authority.

## The adapter boundary

Application services call `ScopeAuthorizer` and `ResourceAuthorizer` (in
`commerce-security-common`). The real SpiceDB client sits behind the `AuthorizationClient`
interface, with a fake for tests. `scripts/verify-architecture.sh` enforces that the gate
authorizers are used only in the service layer, never in controllers or the domain.
