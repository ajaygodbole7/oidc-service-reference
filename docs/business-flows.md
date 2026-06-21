# Business flows

The commerce flows a user drives, and the requests behind them. Every protected request runs the
four-gate ladder ([authorization-model.md](authorization-model.md)); this is the functional view.

## Browse the catalog (anonymous)

Product listing and detail are public. The SPA calls `GET /api/catalog/products` and
`GET /api/catalog/products/{id}` with no session. APISIX routes these without the `bff-session`
plugin, so no bearer is attached and Catalog Service serves them as anonymous reads. Writing the
catalog needs `catalog:write` plus the `store:main#manage` relationship, so only a merchant can
change products.

## Sign in

The user clicks sign in and the SPA navigates to `/auth/login`. Auth Service runs the OIDC
Authorization Code flow with Keycloak (returning to `/auth/callback/idp`), creates a server-side
session in Valkey, and sets the `__Host` session cookie and the `XSRF-TOKEN` cookie. No token
reaches the browser. See [token-model.md](token-model.md).

## Build a cart

Adding the first item creates the cart:

1. `POST /api/cart/items`. Gate 3 requires `cart:write`.
2. The service resolves the caller's cart by subject. On first add it generates the cart id
   server-side, writes `cart:{id}#owner@user:{subject}` to SpiceDB, and persists. The client
   never chooses the id.
3. Later `GET /api/cart`, `POST /api/cart/items`, and `DELETE /api/cart/items/{productId}` resolve
   the same cart and pass gate 4 (`cart:{id}#read` or `#write`) for the owner only.

A request for another user's cart (`GET /api/carts/{id}`) is denied at gate 4.

## Check out

`POST /api/orders/checkout`. Gate 3 requires `orders:write`. The flow:

1. Resolve the caller's cart and authorize it (gate 4).
2. Claim the idempotency key (the `Idempotency-Key` header) before any payment, so a retry cannot
   double-charge.
3. Authorize payment server-to-server: Order Service calls Payment Service with a
   client-credentials token (`aud=payment-service`), never the user's token.
4. Persist the order and link the idempotency record in one transaction, then write
   `order:{id}#owner@user:{subject}` to SpiceDB.

Replaying the same key with the same body returns the same order without re-charging. The same
key with a different body is rejected with `409` before payment runs.

## Read and cancel an order

- `GET /api/orders/{id}`. Gate 3 requires `orders:read`; gate 4 is `order:{id}#read`, which the
  schema grants to the owner or an assigned support user.
- `POST /api/orders/{id}/cancel`. Gate 3 requires `orders:write`; gate 4 is `order:{id}#cancel`,
  which the schema grants to the owner only.

A support user can read an order they are assigned to but cannot cancel it. That split lives in
the SpiceDB schema (`read = owner + support`, `cancel = owner`), not in service code.
