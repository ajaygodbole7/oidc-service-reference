import { expect, test } from "@playwright/test";

const aliceClaims = {
  sub: "alice-123",
  preferred_username: "alice",
  roles: ["user"]
};

const cartFixture = {
  id: "cart-alice",
  currency: "USD",
  items: [
    {
      id: "line-1",
      name: "Trail Coffee",
      quantity: 2,
      unitPriceCents: 1299,
      lineTotalCents: 2598
    }
  ],
  subtotalCents: 2598,
  estimatedTaxCents: 214,
  totalCents: 2812
};

const catalogFixture = {
  products: [
    {
      id: "prod-pack",
      sku: "SKU-PACK",
      name: "Camp Pantry Pack",
      currency: "USD",
      priceCents: 4299,
      inventoryStatus: "IN_STOCK"
    }
  ],
  nextCursor: null
};

test("cart shows the login prompt for anonymous users", async ({ page }) => {
  await page.route("**/auth/me", async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.route("**/api/catalog/products", async (route) => {
    await route.fulfill({ json: catalogFixture });
  });

  // The landing route (/) is now the catalog grid (heading "Featured products"),
  // not the old "Catalog" panel page. The anonymous header still offers the
  // Sign in link (return_to=/).
  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Featured products", exact: true })
  ).toBeVisible();
  await expect(page.getByText("Camp Pantry Pack")).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in" })).toHaveAttribute(
    "href",
    `/auth/login?return_to=${encodeURIComponent("/")}`
  );

  // The cart moved to its own /cart route. For an anonymous visitor it renders
  // the SignInPanel ("Sign in to view your cart"). Note /cart renders TWO
  // sign-in links (the header's plus the panel's), so scope the panel's link to
  // <main> and assert its return_to=/cart. (loginHref() derives return_to from
  // the current route, so it is /cart here, not /.)
  await page.goto("/cart");
  await expect(page.getByText("Sign in to view your cart")).toBeVisible();
  await expect(
    page.locator("main").getByTestId("sign-in-link")
  ).toHaveAttribute("href", `/auth/login?return_to=${encodeURIComponent("/cart")}`);
});

test("cart renders same-origin cart data", async ({ page }) => {
  await page.route("**/auth/me", async (route) => {
    await route.fulfill({ json: aliceClaims });
  });
  await page.route("**/api/catalog/products", async (route) => {
    await route.fulfill({ json: catalogFixture });
  });
  await page.route("**/api/cart", async (route) => {
    await route.fulfill({ json: cartFixture });
  });

  // Cart contents render on the /cart route now, not on the landing page.
  await page.goto("/cart");

  await expect(page.getByText("Trail Coffee")).toBeVisible();
  await expect(page.getByText("$28.12")).toBeVisible();
});

test("cart renders empty and error states", async ({ page }) => {
  await page.route("**/auth/me", async (route) => {
    await route.fulfill({ json: aliceClaims });
  });
  await page.route("**/api/catalog/products", async (route) => {
    await route.fulfill({ json: catalogFixture });
  });
  await page.route("**/api/cart", async (route) => {
    await route.fulfill({
      json: {
        id: "cart-alice",
        currency: "USD",
        items: [],
        subtotalCents: 0,
        estimatedTaxCents: 0,
        totalCents: 0
      }
    });
  });

  // Empty and error states are rendered by CartView on the /cart route.
  await page.goto("/cart");
  await expect(page.getByText("Your cart is empty")).toBeVisible();

  await page.route("**/api/cart", async (route) => {
    await route.fulfill({ status: 500 });
  });
  // reload() stays on /cart; CartView's error branch surfaces a role="alert"
  // that embeds the fetchCart error message ("Cart request failed (500)") inside
  // its "We couldn't load your cart: …" wrapper, so the substring match holds.
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("Cart request failed (500)");
});
