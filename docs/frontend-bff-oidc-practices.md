# Frontend OIDC / BFF best practices

How the SPA participates in the OIDC flow **without ever handling tokens**. Per the project
framing, security is the *invisible platform* — it does not appear in the UI. This doc is the
"showcase": it catalogs each practice, where it lives in code, and the test that proves it. The
structural gates (`src/architecture.test.ts`, `src/eslint-boundary.test.ts`,
`scripts/verify-architecture.sh` → `ARCH-FE-TOKEN-BOUNDARY`) enforce the boundary mechanically.

The browser is an OIDC-oblivious client of the BFF. It calls only same-origin `/auth/**` (session)
and `/api/**` (business) routes; the Auth Service holds tokens server-side and the gateway injects
the bearer. No OAuth/OIDC library runs in the browser.

| Practice | Implemented in | Proven by (`src/auth.test.ts` unless noted) |
|---|---|---|
| **No browser tokens.** Access/refresh/`id_token` never reach JS, storage, or SPA-visible cookies. `/auth/me` is sanitized to an allowlisted display DTO; unknown fields (e.g. `access_token`) are dropped. | `auth.ts` (`fetchMe` → `sanitizeUser`/`isUser`) | "sanitizes /auth/me to the allowlisted User DTO" (strips `access_token`); structurally enforced by `architecture.test.ts` + `eslint-boundary.test.ts` (no `localStorage`/`sessionStorage`/token names) |
| **BFF, not SPA-OAuth.** No token endpoint / PKCE / OAuth client in the browser; identity comes from the server's `/auth/me` projection. | `auth.ts` | whole suite (only same-origin `/auth` + `/api`); `ARCH-FE-NO-DIRECT-IDP` gate |
| **CSRF double-submit.** Unsafe methods echo the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN`; safe methods never do. | `auth.ts` (`callApi`, `readCsrfCookie`) | "POST attaches X-XSRF-TOKEN equal to the XSRF-TOKEN cookie…"; "GET … sends NO CSRF header" |
| **Same-origin `/api` only.** `callApi` refuses off-origin and non-`/api` paths before fetching. | `auth.ts` (`isSameOriginApiPath`) | "rejects off-origin and non-/api paths without fetching" |
| **Return-to-login contract.** Every login/step-up entry carries a URL-encoded `return_to` (path + query + hash); a bare `/auth/login` is forbidden. | `auth.ts` (`loginHref`, `stepUpHref`, `currentRoute`) | "loginHref carries the URL-encoded current route…"; "…encode a deep route (path + query + hash) into return_to" |
| **RFC 9470 step-up.** A `401` with `error="insufficient_user_authentication"` routes to `/auth/step-up` (elevate), not full re-login. | `auth.ts` (`callApi` 401 branch) | "routes an RFC 9470 step-up challenge … to /auth/step-up, not /auth/login" |
| **401 hygiene.** A `401` from `/api/**` triggers a top-level navigation to login/step-up — never a navigation to the API URL. | `auth.ts` (`callApi`) | "the 401-no-navigate-to-API contract holds for unsafe methods too" |
| **Anonymous landing reachable.** A `401` from `/auth/me` resolves to `null` (no session) without redirecting, so the anonymous storefront renders; login is user-initiated. | `auth.ts` (`fetchMe`) | "returns null on /auth/me 401 WITHOUT navigating" |
| **Safe logout.** Logout posts with CSRF and follows only a same-origin, opaque continuation handle; absolute IdP URLs and `\`-prefixed open-redirect bypasses are rejected (the SPA is IdP-oblivious and never sees `id_token`). | `auth.ts` (`signOut`, `safeLogoutUrl`, `parseLogoutResponse`) | "posts logout with the double-submit CSRF header…"; "rejects a backslash-prefixed logoutUrl…"; "rejects an absolute IdP logoutUrl…" |

## Data layer (`src/lib/commerce.ts`)

- Anonymous catalog/product reads use same-origin `fetch("/api/catalog/...", { credentials: "include" })` (GET — no CSRF required) with runtime response validation and `encodeURIComponent` on the product id.
- The cart goes through `callApi("/api/cart", …)`, so it inherits CSRF + the 401→login/step-up handling above.
- `imageUrl` is accepted only as a same-origin relative path (`isSameOriginImagePath`).
- **Forward note:** if catalog ever becomes auth-gated, route its reads through `callApi` too, so they pick up the 401→step-up handling.

## Mutations

The current screens are read-only, so `useOptimistic` and write Actions are not yet exercised. When
add-to-cart / checkout land, mutations MUST go through `callApi` (CSRF + 401 handling) and use React
19 Actions (`useActionState` / `useOptimistic`); the sign-out control already demonstrates the
Action pattern (`src/components/AppShell.tsx`).
