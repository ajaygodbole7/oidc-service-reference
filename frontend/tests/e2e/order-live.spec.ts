import { expect, test, type Page } from "@playwright/test";

const APP_ORIGIN = "http://127.0.0.1:5173";
const REALM_NAME = "oidc-service-reference";
const KEYCLOAK_AUTH_RE = new RegExp(
  `realms\\/${REALM_NAME}\\/protocol\\/openid-connect\\/auth`
);

const TOKEN_MATERIAL_RE =
  /\b(?:access_token|refresh_token|id_token)\b|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/i;

type FetchResult = { readonly status: number; readonly body: string };

async function loginAs(page: Page, username: string, password: string = username): Promise<void> {
  await page.goto("/");
  await page.getByRole("link", { name: /sign in/i }).click();
  await page.waitForURL(KEYCLOAK_AUTH_RE);
  await page.fill("#username", username);
  await page.fill("#password", password);
  await Promise.all([
    page.waitForURL(`${APP_ORIGIN}/`),
    page.click("#kc-login")
  ]);
  // Authenticated marker in the routed AppShell: the "Sign out" button (present
  // iff a session exists). The header no longer renders "signed in as".
  await expect(page.getByRole("button", { name: /sign out/i })).toBeVisible();
}

async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const csrf = await page.evaluate(() => {
    const cookie = document.cookie
      .split(";")
      .map((part) => part.trim())
      .find((part) => part.startsWith("XSRF-TOKEN="));
    return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : "";
  });
  expect(csrf, "XSRF-TOKEN cookie must be visible to the SPA for unsafe calls").not.toBe("");
  return { "X-XSRF-TOKEN": csrf };
}

async function apiFetch(page: Page, path: string, init: RequestInit = {}): Promise<FetchResult> {
  return page.evaluate(
    async ({ requestPath, requestInit }) => {
      const headers = { Accept: "application/json", ...(requestInit.headers ?? {}) };
      const res = await fetch(requestPath, { credentials: "include", ...requestInit, headers });
      return { status: res.status, body: await res.text() };
    },
    { requestPath: path, requestInit: init }
  );
}

function checkoutBody(paymentMethodId: string, shippingPostalCode: string): string {
  return JSON.stringify({ paymentMethodId, shippingPostalCode });
}

function harnessKey(suffix: string): string {
  return `${process.env.ORDER_PAYMENT_HARNESS_RUN_ID ?? "local"}-${suffix}`;
}

test("SEC-CHECKOUT-IDEMPOTENT-REPLAY: same key + same body returns the same order", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await loginAs(page, "alice");
  const csrf = await csrfHeaders(page);
  const key = harnessKey("idem-replay");
  const body = checkoutBody("pm-card-1", "94105");

  const first = await apiFetch(page, "/api/orders/checkout", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json", "Idempotency-Key": key },
    body
  });
  expect(first.status).toBe(201);
  expect(first.body).not.toMatch(TOKEN_MATERIAL_RE);
  const firstId = (JSON.parse(first.body) as { id?: string }).id;
  expect(typeof firstId).toBe("string");

  // Same key + identical body: the stored order is returned, payment is authorized once.
  const replay = await apiFetch(page, "/api/orders/checkout", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json", "Idempotency-Key": key },
    body
  });
  expect(replay.status).toBe(201);
  expect((JSON.parse(replay.body) as { id?: string }).id).toBe(firstId);
  expect(replay.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-CHECKOUT-IDEMPOTENCY-COLLISION: same key + different body is rejected before payment", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await loginAs(page, "alice");
  const csrf = await csrfHeaders(page);
  const key = harnessKey("idem-collision");

  const first = await apiFetch(page, "/api/orders/checkout", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json", "Idempotency-Key": key },
    body: checkoutBody("pm-card-1", "94105")
  });
  expect(first.status).toBe(201);

  // Same key, different body: must conflict (409) and not authorize a second payment.
  const collision = await apiFetch(page, "/api/orders/checkout", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json", "Idempotency-Key": key },
    body: checkoutBody("pm-card-2", "10001")
  });
  expect(collision.status).toBe(409);
  expect(collision.body).not.toMatch(TOKEN_MATERIAL_RE);
});
