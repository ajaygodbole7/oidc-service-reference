import { expect, test, type Page } from "@playwright/test";

// Live four-gate acceptance for the checkout + cart-mutation slice. Drives the REAL UI through
// APISIX against the running stack: the add-to-cart Action -> cart-service (gates 2/3/4), the
// checkout Action -> order-service -> payment S2S + idempotency, then the /orders/$orderId
// confirmation route. Mocked counterpart: tests/e2e/checkout.spec.ts.
//
// Self-cleaning: checkout READS but does not clear the cart, and cart-live's empty-cart case
// requires alice's cart to be empty (the cart harness keeps alice/bob carts across runs). So this
// test starts and ends by removing every line (which also exercises the remove-item Action) and
// re-asserts the token boundary across the new flows.

const APP_ORIGIN = "http://127.0.0.1:5173";
const REALM_NAME = "oidc-service-reference";
const KEYCLOAK_AUTH_RE = new RegExp(`realms\\/${REALM_NAME}\\/protocol\\/openid-connect\\/auth`);
const TOKEN_MATERIAL_RE =
  /\b(?:access_token|refresh_token|id_token)\b|[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}/i;

// Canonical Flyway seed product (V1__create_products.sql; the id is asserted in
// PostgresProductRepositoryTest as MUG_ID -> sku MUG-001, "Starter Mug").
const SEEDED_PRODUCT_ID = "6801HWW000000";

async function loginAs(page: Page, username: string, password: string = username): Promise<void> {
  await page.goto("/");
  await page.getByRole("link", { name: /sign in/i }).click();
  await page.waitForURL(KEYCLOAK_AUTH_RE);
  await page.fill("#username", username);
  await page.fill("#password", password);
  await Promise.all([page.waitForURL(`${APP_ORIGIN}/`), page.click("#kc-login")]);
  await expect(page.getByRole("button", { name: /sign out/i })).toBeVisible();
}

// Remove every line through the UI so alice's cart returns to empty.
async function emptyTheCart(page: Page): Promise<void> {
  await page.goto("/cart");
  // Wait for the cart query to settle first: the route renders a loading skeleton (no remove
  // buttons and no empty-state text), so a bare count() would race the fetch and see zero.
  const anyRemove = page.getByRole("button", { name: /^remove/i });
  await expect(page.getByText("Your cart is empty").or(anyRemove.first())).toBeVisible({
    timeout: 15000
  });
  for (let i = 0; i < 12; i++) {
    const count = await anyRemove.count();
    if (count === 0) break;
    await anyRemove.first().click();
    await expect(anyRemove).toHaveCount(count - 1, { timeout: 10000 });
  }
  await expect(page.getByText("Your cart is empty")).toBeVisible();
}

test("checkout slice: add to cart through the four gates, place order, see confirmation", async ({ page }) => {
  test.skip(process.env.E2E_FULL_STACK !== "1", "requires live local stack");

  await loginAs(page, "alice");

  // Start from a known-empty cart (a prior run may have left items; checkout does not clear).
  await emptyTheCart(page);

  // 1. Add a real seeded product from its detail page -> POST /api/cart/items (gates 2/3/4).
  await page.goto(`/products/${SEEDED_PRODUCT_ID}`);
  await expect(page.getByRole("button", { name: /add to cart/i })).toBeVisible();
  await expect(page.getByText(/starter mug/i)).toBeVisible();
  await page.getByRole("button", { name: /add to cart/i }).click();

  // 2. The header badge reflects the add; open the cart and confirm the item is there.
  await expect(page.getByTestId("cart-nav")).toContainText("1");
  await page.getByTestId("cart-nav").click();
  await page.waitForURL(/\/cart$/);
  // CartView resolves the line's display name from the catalog (it joins the cart item's product id
  // to the catalog product), so the cart shows the real product name, not the cart-service id echo.
  await expect(page.getByText(/starter mug/i)).toBeVisible();

  // 3. Check out -> order-service reads the cart (gate-4 CART_READ) -> payment S2S + idempotency.
  await page.getByLabel(/postal code/i).fill("94105");
  await page.getByRole("button", { name: /place order/i }).click();

  // 4. Lands on the confirmation route with a server-minted order id.
  await page.waitForURL(/\/orders\/[0-9A-Za-z]+$/);
  await expect(page.getByRole("heading", { name: /order placed/i })).toBeVisible();
  const orderId = page.url().split("/orders/")[1];
  expect(orderId, "confirmation URL carries an order id").toBeTruthy();
  await expect(page.getByText(orderId!)).toBeVisible();

  // 5. Token boundary across the new flows: no token material in storage, cookies, or the DOM.
  const browserState = await page.evaluate(() => {
    const dump = (store: Storage) =>
      Object.keys(store).map((key) => `${key}=${store.getItem(key) ?? ""}`).join(";");
    return `${dump(localStorage)}|${dump(sessionStorage)}|${document.cookie}`;
  });
  expect(browserState).not.toMatch(TOKEN_MATERIAL_RE);
  expect(await page.content()).not.toMatch(TOKEN_MATERIAL_RE);

  // 6. Self-clean: leave alice's cart empty so the cart-live empty-cart case still holds.
  await emptyTheCart(page);
});
