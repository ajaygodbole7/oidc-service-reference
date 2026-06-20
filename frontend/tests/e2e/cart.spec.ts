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
      name: "Camp Pantry Pack",
      description: "Shelf-stable staples for a weekend away.",
      priceCents: 4299,
      currency: "USD",
      inventoryStatus: "in_stock"
    }
  ]
};

test("cart shows the login prompt for anonymous users", async ({ page }) => {
  await page.route("**/auth/me", async (route) => {
    await route.fulfill({ status: 401 });
  });
  await page.route("**/api/catalog/products", async (route) => {
    await route.fulfill({ json: catalogFixture });
  });

  await page.goto("/");

  await expect(page.getByRole("heading", { name: "Catalog", exact: true })).toBeVisible();
  await expect(page.getByText("Camp Pantry Pack")).toBeVisible();
  await expect(page.getByRole("link", { name: "Sign in" })).toHaveAttribute(
    "href",
    `/auth/login?return_to=${encodeURIComponent("/")}`
  );
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

  await page.goto("/");

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

  await page.goto("/");
  await expect(page.getByText("Your cart is empty")).toBeVisible();

  await page.route("**/api/cart", async (route) => {
    await route.fulfill({ status: 500 });
  });
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("Cart request failed (500)");
});
