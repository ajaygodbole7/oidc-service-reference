import { expect, test, type Page } from "@playwright/test";

const APP_ORIGIN = "http://127.0.0.1:5173";
const REALM_NAME = "oidc-service-reference";
const KEYCLOAK_AUTH_RE = new RegExp(
  `realms\\/${REALM_NAME}\\/protocol\\/openid-connect\\/auth`
);

// Same token-material guard the cart live suite uses: no access/refresh/id token
// or JWT-shaped string may ever appear in a browser-visible response body.
const TOKEN_MATERIAL_RE =
  /access_token|refresh_token|id_token|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/i;

// Canonical product TSID for the seeded "starter-mug" product (catalog now mints
// TSIDs; ids are no longer human slugs).
const STARTER_MUG_ID = "6801HWW000000";

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
  await expect(page.getByText(/signed in as/i)).toBeVisible();
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

// The catalog POST no longer accepts a client-supplied product id; the server mints
// the TSID and returns it in the response. `label` only seeds the sku/name.
function newProductBody(label: string): string {
  return JSON.stringify({
    sku: `SKU-${label}`,
    name: `Live ${label}`,
    price: 19.99,
    inventoryStatus: "IN_STOCK"
  });
}

// Each test runs in a fresh browser context (clean cookies), so the per-test login
// is not reused across the anonymous / non-merchant / merchant cases (avoids Keycloak
// SSO carrying one identity into the next). The full-stack gate forces one worker.

test("SEC-CATALOG-ANONYMOUS-READ-ONLY: anonymous can read but cannot write", async ({ page }) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await page.goto("/");

  const list = await apiFetch(page, "/api/catalog/products");
  expect(list.status).toBe(200);
  expect(list.body).not.toMatch(TOKEN_MATERIAL_RE);

  const detail = await apiFetch(page, `/api/catalog/products/${STARTER_MUG_ID}`);
  expect(detail.status).toBe(200);
  expect(detail.body).not.toMatch(TOKEN_MATERIAL_RE);

  const anonWrite = await apiFetch(page, "/api/catalog/products", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: newProductBody("anon-should-fail")
  });
  expect([401, 403]).toContain(anonWrite.status);
  expect(anonWrite.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-CATALOG-ANONYMOUS-READ-ONLY: authenticated non-merchant write denied by the resource gate", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  // alice carries the catalog:write scope (gate 3) but has no store:main#manage
  // relationship in SpiceDB, so gate 4 must deny the write.
  await loginAs(page, "alice");
  const csrf = await csrfHeaders(page);

  const write = await apiFetch(page, "/api/catalog/products", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json" },
    body: newProductBody("alice-should-fail")
  });
  expect(write.status).toBe(403);
  expect(write.body).not.toMatch(TOKEN_MATERIAL_RE);
});

test("SEC-CATALOG-ANONYMOUS-READ-ONLY: merchant write succeeds with scope + store:main#manage", async ({
  page
}) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await loginAs(page, "merchant");
  const csrf = await csrfHeaders(page);

  const write = await apiFetch(page, "/api/catalog/products", {
    method: "POST",
    headers: { ...csrf, "Content-Type": "application/json" },
    body: newProductBody("merchant-live-product")
  });
  expect(write.status).toBe(201);
  expect(write.body).not.toMatch(TOKEN_MATERIAL_RE);

  // The server mints the product id; capture it from the create response.
  const mintedId = (JSON.parse(write.body) as { id?: string }).id;
  expect(typeof mintedId).toBe("string");

  // the newly created product is now anonymously readable through the catalog
  const created = await apiFetch(page, `/api/catalog/products/${mintedId}`);
  expect(created.status).toBe(200);
  expect(created.body).not.toMatch(TOKEN_MATERIAL_RE);
});
