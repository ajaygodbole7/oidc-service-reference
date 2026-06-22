import { expect, test } from "@playwright/test";

// Mocked cart-mutation + checkout flow (no live stack), the same style as cart.spec.ts's
// token-boundary smoke: it drives the React 19 Actions (add-to-cart, checkout) and the
// useOptimistic cart count through stubbed /api responses, so the inner red->green loop
// needs only the Vite dev server. The live four-gate proof of the same flow lives in
// cart-live.spec.ts / order-live.spec.ts.
//
// RED until: the product-detail "Add to cart" Action, the header cart-count badge, the
// /cart checkout form, and the /orders/$orderId confirmation route exist.

const authedUser = { sub: "alice", preferred_username: "alice", roles: ["user"] };

const product = {
  id: "prod-pack",
  sku: "SKU-PACK",
  name: "Camp Pantry Pack",
  currency: "USD",
  priceCents: 4299,
  inventoryStatus: "IN_STOCK"
};
const catalogFixture = { products: [product], nextCursor: null };

const emptyCart = {
  id: "cart-alice",
  currency: "USD",
  items: [],
  subtotalCents: 0,
  estimatedTaxCents: 0,
  totalCents: 0
};
const cartWithPack = {
  id: "cart-alice",
  currency: "USD",
  items: [
    { id: "line-1", name: "Camp Pantry Pack", quantity: 1, unitPriceCents: 4299, lineTotalCents: 4299 }
  ],
  subtotalCents: 4299,
  estimatedTaxCents: 354,
  totalCents: 4653
};
const order = {
  id: "ord-001",
  status: "CONFIRMED",
  sourceCartId: "cart-alice",
  currency: "USD",
  totalCents: 4653,
  createdAt: "2026-06-22T12:00:00Z",
  lines: [
    { productId: "prod-pack", name: "Camp Pantry Pack", quantity: 1, unitPriceCents: 4299, lineTotalCents: 4299 }
  ]
};

test("add to cart from product detail, then check out to an order confirmation", async ({ page }) => {
  let cart: typeof cartWithPack = emptyCart;

  await page.route("**/auth/me", (route) => route.fulfill({ json: authedUser }));
  await page.route("**/api/catalog/products", (route) => route.fulfill({ json: catalogFixture }));
  await page.route("**/api/catalog/products/prod-pack", (route) => route.fulfill({ json: product }));
  // GET /api/cart reflects whatever the add-item POST last set.
  await page.route("**/api/cart", (route) => route.fulfill({ json: cart }));
  await page.route("**/api/cart/items", async (route) => {
    expect(route.request().method()).toBe("POST");
    cart = cartWithPack;
    await route.fulfill({ status: 201, json: cartWithPack });
  });
  await page.route("**/api/orders/checkout", async (route) => {
    expect(route.request().method()).toBe("POST");
    // The checkout Action MUST mint an Idempotency-Key (header is lower-cased by Playwright).
    expect(route.request().headers()["idempotency-key"] ?? "").not.toBe("");
    await route.fulfill({ status: 201, json: order });
  });
  await page.route("**/api/orders/ord-001", (route) => route.fulfill({ json: order }));

  // 1. Add to cart from the product detail page.
  await page.goto("/products/prod-pack");
  await page.getByRole("button", { name: /add to cart/i }).click();

  // 2. The header cart badge reflects the item (optimistic, then confirmed by refetch).
  await expect(page.getByTestId("cart-nav")).toContainText("1");

  // 3. Go to the cart; the added item is shown.
  await page.getByTestId("cart-nav").click();
  await expect(page).toHaveURL(/\/cart$/);
  await expect(page.getByText("Camp Pantry Pack")).toBeVisible();

  // 4. Fill the checkout form and place the order (payment method has a demo default).
  await page.getByLabel(/postal code/i).fill("94105");
  await page.getByRole("button", { name: /place order/i }).click();

  // 5. Lands on the order confirmation route, showing the minted order id.
  await expect(page).toHaveURL(/\/orders\/ord-001$/);
  await expect(page.getByRole("heading", { name: /order placed/i })).toBeVisible();
  await expect(page.getByText("ord-001")).toBeVisible();
});
