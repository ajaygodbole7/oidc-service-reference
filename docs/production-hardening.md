# Production hardening

oidc-service-reference is a local reference. It runs on Docker Compose with a single Keycloak
realm, per-service local Postgres databases, and loopback-bound dev secrets. The four-gate
ladder, the BFF token boundary, and the server-to-server checkout path are real and verified
here, but the operational layer a real deployment adds is not. This document lists those
production concerns, why each is left out of the default, and where it would plug into the
architecture in [architecture.md](architecture.md). Most of what follows is deferred, with two
exceptions called out where they appear: the optimistic-lock version column and the TSID storage
ids, noted below as the in-place foundation that a production hardening builds on rather than
something a deployment still has to add.

The decision throughout is to keep the reference focused on the authorization model. Operational
concerns (transport identity, scheduling, data operations, telemetry) are orthogonal to the
four gates and would add infrastructure that obscures the code the repo exists to show.

## mTLS / SPIFFE for service identity

Today the gateway, auth service, and domain services share a trusted internal Docker network.
The order-to-payment call authenticates with a client-credentials token (`aud=payment-service`),
and that token is the only thing Payment validates about the caller (see
[token-model.md](token-model.md)). Transport between services is plain HTTP on the Compose
network.

A production deployment gives each service a cryptographic workload identity (SPIFFE SVIDs,
issued by SPIRE or the platform) and requires mutual TLS on every service-to-service hop, so a
service proves what it is at the transport layer independently of any bearer token. This is
deferred because the reference assumes the internal network is trusted and the audience-scoped
S2S token already carries caller attribution; mTLS adds a certificate authority, rotation, and
SVID plumbing that does not change the authorization logic.

Where it plugs in: the connections between APISIX and the domain services, between Order Service
and Payment Service, and between every service and SpiceDB, Keycloak, Valkey, and Postgres. The
client-credentials token stays as the authorization fact; the SVID becomes the transport
identity underneath it.

## Service mesh

Today there is no mesh. The gateway routes are defined in `bff-session.lua` and the APISIX
config, and services call each other by Compose DNS name.

A mesh (Istio, Linkerd, or equivalent) is where mTLS, retries, timeouts, traffic policy, and
per-hop telemetry would normally be enforced once rather than per service. It is deferred
because the reference has one cross-service call (order to payment) and a fixed routing table,
so a mesh would be infrastructure with almost nothing to manage. Mesh-injected mTLS is one
concrete way to deliver the SPIFFE identity described above.

Where it plugs in: a sidecar alongside each service in the [architecture.md](architecture.md)
component list, taking over the transport concerns the application code currently does not
handle.

## RFC 8693 token exchange for per-resource downscoped tokens

Today the user access token is minted once, at the auth-code exchange. The BFF holds it in the
session record, and APISIX injects that same token as a phantom token on every `/api/**`
request. There is no resolve-time `resource` parameter and no resource-specific minting; one
`commerce-api` audience covers all user-facing services (see [token-model.md](token-model.md)).
Payment Service authorizes the calling service from the S2S token and trusts `userSub` as
command data, not as cryptographic user provenance.

RFC 8693 token exchange lets a service exchange the inbound token for a narrower one scoped to a
specific downstream resource or audience. It is deferred because the single broad audience plus
per-request SpiceDB checks already enforce least privilege at the resource gate, and exchange
adds a token-minting round trip on the hot path. The case where it matters most here is
checkout: an exchanged token would let Payment verify the user cryptographically instead of
reading `userSub` from the command.

Where it plugs in: the auth service `/internal/resolve` path (mint a downscoped token per target
service) and the Order Service to Payment Service call (exchange for a token that carries
verifiable user provenance, replacing the trusted `userSub` field).

## Event bus / async messaging

Today checkout is synchronous. Order Service obtains a client-credentials token and calls
Payment Service directly on the internal network, in-request, and the order outcome depends on
that call returning (see the order-to-payment section of [architecture.md](architecture.md)).

A production deployment often makes order and payment communicate through a broker (Kafka,
NATS, or similar) so the two services decouple, retry independently, and survive each other's
downtime. It is deferred because synchronous, idempotent S2S keeps the call path readable and
the failure modes explicit, which is what the reference is demonstrating; a broker adds delivery
semantics, consumer groups, and an outbox that are a study in themselves.

Where it plugs in: the Order Service to Payment Service edge. The existing idempotency key
(carried in the synchronous command) becomes the message dedup key, and the in-request call
becomes a published command plus a consumed result.

## Kubernetes

Today the stack is Docker Compose: one file brings up Keycloak, Valkey, Postgres, APISIX, the
auth service, SpiceDB, and the domain services. The frontend runs separately (the Vite dev server).

Kubernetes is the production scheduler: Deployments, Services, health-gated rollouts, secrets,
and horizontal scaling. It is deferred because Compose is the right granularity for a single-host
reference a reader runs locally; the application code and the four gates are identical under
either scheduler.

Where it plugs in: each Compose service becomes a Deployment plus Service, the dev secrets become
managed secrets, and the Compose healthchecks become readiness and liveness probes. This is also
the layer a service mesh and SPIFFE identity attach to.

## Multi-region

Today everything runs in one place, on one host.

A multi-region deployment replicates the stack across regions and has to answer where session
state, the SpiceDB datastore, and the per-service Postgres databases live, and how a request in
one region reads a relationship written in another. It is deferred because the reference uses a
single Valkey session store, a single SpiceDB, and local Postgres, and the `fully_consistent`
SpiceDB reads the gates rely on have a different cost and meaning once the datastore spans
regions.

Where it plugs in: session storage (Valkey), the SpiceDB datastore, and persistence, all of
which are single-instance here. The consistency model of gate 4 is the part that needs the most
care across regions.

## Production database operations

Today each service has its own local Postgres database (`catalog_db`, `cart_db`, `order_db`,
`payment_db`) on a single Compose container, with per-service Flyway migrations applied at
startup. Ownership columns are stored as business data only and never substitute for the gate-4
SpiceDB check.

A real deployment adds high availability (primary plus replicas, failover), backups and
point-in-time recovery, a connection pool sized for load, and a migration process that runs as a
controlled operational step rather than at boot, with rollback and expand-then-contract for
schema changes. These are deferred because the reference needs persistence to prove the gates
survive a real datastore, not a database operations practice; the Flyway migrations show the
schema, which is the part that matters for the code.

Where it plugs in: the Postgres layer in [architecture.md](architecture.md). The repository
interfaces and Flyway migrations stay; HA topology, pooling, backup, and migration operations
sit underneath them and do not touch the four gates or the ownership-column rule.

Two concurrency facts are already built here, and one cleanup is deferred. Concurrent writes to
the same aggregate (for example two `cancelOrder` calls, or two updates of one cart) are detected,
not last-writer-wins: cart, order, and payment carry an optimistic-lock version column
(`CartRow`, `OrderRow`, `PaymentRow` each have a `@Version` field, and every `V1__*.sql` declares
`version BIGINT NOT NULL DEFAULT 0`). A stale update matches zero rows and Spring Data raises
`OptimisticLockingFailureException`, which the shared web starter maps to an RFC 9457 `409`. The
resource ids are TSIDs (`HypersistenceTsidGenerator`), generated server-side, which is also built.
What a deployment still adds on top is automatic retry on the optimistic failure: `cancelOrder`
surfaces the lock conflict to the caller as a `409` without retrying, by design, so the caller
decides whether to re-read and re-apply. Separately, the SpiceDB owner relationship for a new cart
or order is written just before the row is persisted; on a concurrent create-on-first-add race the
request recovers by re-resolving to the winning row, but the owner tuple for the discarded id is
left behind. It is harmless (no aggregate references it), and a production deployment adds a
periodic relationship-reconciliation job to remove tuples with no backing row.

## Observability / SIEM

Today the Security Trace records per-request gate evidence (browser token absent, resolved
session, injected-token fingerprint, scope decision, resource decision, no raw token) into Valkey
under a short TTL, keyed by `X-Trace-Id`. Read access is harness and internal only, under local
and test profiles, and it is verifier evidence rather than a telemetry pipeline. `SecurityAudit`
records gate decisions in the same spirit.

A production deployment ships these signals to real telemetry: structured logs, metrics, and
traces to a backend (OpenTelemetry collector, then a metrics and tracing store), and the security
audit events to a SIEM for retention, correlation, and alerting. It is deferred because the short
TTL trace store is enough to prove each gate fired in the verification harness, and a telemetry
stack is operational weight the reference does not need to make its point.

Where it plugs in: the Security Trace and `SecurityAudit` are the emission points. The trace shape
already excludes raw tokens, so it can be exported as-is; the audit stream, extended with
`resource=`, is the SIEM feed.

## Supply-chain pinning

Today the build pins application dependency versions, but container images are referenced by tag
and the build produces no signed provenance.

A production deployment pins base and runtime images by digest, generates an SBOM per artifact,
and signs images and provenance (Cosign, SLSA), so a deployed artifact is verifiable end to end.
This is deferred because it is a release-pipeline practice that sits outside the running system
and does not change how a request is authorized.

Where it plugs in: the Compose image references and the build, ahead of any registry the images
are published to.

## Provider and authorization-service adapters

Two adapter swaps are noted as future work because the seams for them already exist.

A Cognito scope-profile adapter: Keycloak is the local IdP, and the services validate standard
OIDC claims (issuer, RS256 signature, expiry, `typ` of `JWT` or `at+JWT`, audience, scopes)
rather than provider-specific shapes. Provider specifics stay in configuration. Cognito presents
scopes and audiences differently from Keycloak, so a deployment on Cognito is a scope-profile and
config change, not a code change. Where it plugs in: the JWT validator chain and realm-equivalent
configuration; no service code branches on the provider brand.

OpenFGA as an alternative authorization-service adapter: every service reaches gate 4 through
`ResourceAuthorizer`, which depends on the `AuthorizationClient` port; the SpiceDB SDK lives only
in the adapter that implements that port, and no controller or domain object calls it directly.
Swapping SpiceDB for OpenFGA (another Zanzibar-style relationship store) is therefore a second
implementation of the same `AuthorizationClient` interface, with an equivalent schema and seed.
Where it plugs in: a new adapter behind the existing port; `ResourceAuthorizer` and the gate-4
checks are unchanged.
