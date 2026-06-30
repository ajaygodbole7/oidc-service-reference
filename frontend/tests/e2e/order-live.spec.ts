import { expect, test, type Page } from "@playwright/test";
import { KEYCLOAK_AUTH_RE, TOKEN_MATERIAL_RE, loginAs } from "./helpers";

type FetchResult = { readonly status: number; readonly body: string };

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
