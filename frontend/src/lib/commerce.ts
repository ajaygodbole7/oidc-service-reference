// Commerce data layer: types, same-origin BFF fetchers, runtime validators, and
// display formatters for the catalog + cart. Moved out of App.tsx so screen
// components and the TanStack Query hooks (src/lib/queries.ts) share one source
// of truth. No tokens, no auth state — fetchers call same-origin /api/** only
// (catalog is anonymous; cart goes through auth.ts callApi for CSRF + 401
// handling). The token boundary stays in auth.ts.

import { callApi } from "@/auth";

export type CartItem = {
  readonly id: string;
  readonly productId: string;
  readonly name: string;
  readonly quantity: number;
  readonly unitPriceCents: number;
  readonly lineTotalCents: number;
};

export type Cart = {
  readonly id: string;
  readonly currency: string;
  readonly items: readonly CartItem[];
  readonly subtotalCents: number;
  readonly estimatedTaxCents: number;
  readonly totalCents: number;
};

type WireCartItem = Omit<CartItem, "productId"> & {
  readonly productId?: string;
};

type WireCart = Omit<Cart, "items"> & {
  readonly items: readonly WireCartItem[];
};

export type OrderLine = {
  readonly productId: string;
  readonly name?: string;
  readonly quantity: number;
  readonly unitPriceCents: number;
  readonly lineTotalCents: number;
};

// Mirrors order-service/web/OrderResponse (incl. its nested Line record):
// { id, status, sourceCartId, currency, totalCents, createdAt, lines:[Line] }.
// The backend Line record is { productId, quantity, unitPriceCents, lineTotalCents };
// `name` is an optional convenience field the API may add for display, so it
// stays optional here and the validator only requires the backend's four fields.
export type Order = {
  readonly id: string;
  readonly status: string;
  readonly sourceCartId: string;
  readonly currency: string;
  readonly totalCents: number;
  readonly createdAt: string;
  readonly lines: readonly OrderLine[];
};

export type InventoryStatus = "IN_STOCK" | "LOW_STOCK" | "OUT_OF_STOCK";

export type CatalogProduct = {
  readonly id: string;
  readonly sku?: string;
  readonly name: string;
  readonly description?: string;
  readonly priceCents: number;
  readonly currency: string;
  readonly inventoryStatus: InventoryStatus;
  readonly imageUrl?: string;
};

// --- Fetchers ---------------------------------------------------------------

export async function fetchCatalogProducts(signal: AbortSignal): Promise<readonly CatalogProduct[]> {
  const response = await fetch("/api/catalog/products", {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal
  });
  if (signal.aborted) throw new DOMException("Request aborted", "AbortError");
  if (!response.ok) throw new Error(`Catalog request failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isCatalogResponse(body)) throw new Error("Catalog response had an unexpected shape");
  return body.products;
}

export async function fetchProduct(productId: string, signal: AbortSignal): Promise<CatalogProduct> {
  const response = await fetch(`/api/catalog/products/${encodeURIComponent(productId)}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
    signal
  });
  if (signal.aborted) throw new DOMException("Request aborted", "AbortError");
  if (!response.ok) throw new Error(`Product request failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isCatalogProduct(body)) throw new Error("Product response had an unexpected shape");
  return body;
}

export async function fetchCart(signal: AbortSignal): Promise<Cart> {
  const response = await callApi("/api/cart", {
    headers: { Accept: "application/json" },
    signal
  });
  if (signal.aborted) throw new DOMException("Request aborted", "AbortError");
  if (response.status === 404) return emptyCart();
  if (!response.ok) throw new Error(`Cart request failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isWireCart(body)) throw new Error("Cart response had an unexpected shape");
  return normalizeCart(body);
}

export function emptyCart(): Cart {
  return {
    id: "current",
    currency: "USD",
    items: [],
    subtotalCents: 0,
    estimatedTaxCents: 0,
    totalCents: 0
  };
}

// --- Mutations (all through callApi → CSRF + 401→login/step-up) -------------
// The backend takes unitPrice as a 2-decimal BigDecimal string (e.g. 4299 cents
// → "42.99"), so the add carries the price the user saw, not a re-derived one.

export async function addCartItem(
  productId: string,
  quantity: number,
  unitPriceCents: number
): Promise<Cart> {
  const response = await callApi("/api/cart/items", {
    method: "POST",
    body: { productId, quantity, unitPrice: formatPriceString(unitPriceCents) }
  });
  if (!response.ok) throw new Error(`Add to cart failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isWireCart(body)) throw new Error("Cart response had an unexpected shape");
  return normalizeCart(body);
}

export async function removeCartItem(productId: string): Promise<Cart> {
  const response = await callApi(`/api/cart/items/${encodeURIComponent(productId)}`, {
    method: "DELETE"
  });
  if (!response.ok) throw new Error(`Remove from cart failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isWireCart(body)) throw new Error("Cart response had an unexpected shape");
  return normalizeCart(body);
}

// Checkout. The Idempotency-Key is minted by the caller (crypto.randomUUID) so
// a retried submit collapses to one order server-side; the SPA never reuses a
// stale key. paymentMethodId is a demo handle — payment provenance is a backend
// concern, the browser never holds card data.
export async function placeOrder(
  command: { readonly paymentMethodId: string; readonly shippingPostalCode: string },
  idempotencyKey: string
): Promise<Order> {
  const response = await callApi("/api/orders/checkout", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: command
  });
  if (!response.ok) throw new Error(`Checkout failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isOrder(body)) throw new Error("Order response had an unexpected shape");
  return body;
}

export async function fetchOrder(orderId: string, signal: AbortSignal): Promise<Order> {
  const response = await callApi(`/api/orders/${encodeURIComponent(orderId)}`, {
    headers: { Accept: "application/json" },
    signal
  });
  if (signal.aborted) throw new DOMException("Request aborted", "AbortError");
  if (!response.ok) throw new Error(`Order request failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isOrder(body)) throw new Error("Order response had an unexpected shape");
  return body;
}

// --- Validators -------------------------------------------------------------

function isCatalogResponse(value: unknown): value is { readonly products: readonly CatalogProduct[] } {
  if (value === null || typeof value !== "object") return false;
  const catalog = value as Record<string, unknown>;
  return Array.isArray(catalog.products) && catalog.products.every(isCatalogProduct);
}

export function isCatalogProduct(value: unknown): value is CatalogProduct {
  if (value === null || typeof value !== "object") return false;
  const product = value as Record<string, unknown>;
  return (
    typeof product.id === "string" &&
    (product.sku === undefined || typeof product.sku === "string") &&
    typeof product.name === "string" &&
    (product.description === undefined || typeof product.description === "string") &&
    isNonNegativeInteger(product.priceCents) &&
    isCurrencyCode(product.currency) &&
    isInventoryStatus(product.inventoryStatus) &&
    (product.imageUrl === undefined || isSameOriginImagePath(product.imageUrl))
  );
}

function isWireCart(value: unknown): value is WireCart {
  if (value === null || typeof value !== "object") return false;
  const cart = value as Record<string, unknown>;
  return (
    typeof cart.id === "string" &&
    typeof cart.currency === "string" &&
    Array.isArray(cart.items) &&
    cart.items.every(isCartItem) &&
    isCurrencyCode(cart.currency) &&
    isNonNegativeInteger(cart.subtotalCents) &&
    isNonNegativeInteger(cart.estimatedTaxCents) &&
    isNonNegativeInteger(cart.totalCents)
  );
}

export function isOrder(value: unknown): value is Order {
  if (value === null || typeof value !== "object") return false;
  const order = value as Record<string, unknown>;
  return (
    typeof order.id === "string" &&
    typeof order.status === "string" &&
    typeof order.sourceCartId === "string" &&
    isCurrencyCode(order.currency) &&
    isNonNegativeInteger(order.totalCents) &&
    typeof order.createdAt === "string" &&
    Array.isArray(order.lines) &&
    order.lines.every(isOrderLine)
  );
}

function isOrderLine(value: unknown): value is OrderLine {
  if (value === null || typeof value !== "object") return false;
  const line = value as Record<string, unknown>;
  return (
    typeof line.productId === "string" &&
    (line.name === undefined || typeof line.name === "string") &&
    isNonNegativeInteger(line.quantity) &&
    isNonNegativeInteger(line.unitPriceCents) &&
    isNonNegativeInteger(line.lineTotalCents)
  );
}

function isCartItem(value: unknown): value is WireCartItem {
  if (value === null || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return (
    typeof item.id === "string" &&
    (item.productId === undefined || typeof item.productId === "string") &&
    typeof item.name === "string" &&
    isNonNegativeInteger(item.quantity) &&
    isNonNegativeInteger(item.unitPriceCents) &&
    isNonNegativeInteger(item.lineTotalCents)
  );
}

function normalizeCart(cart: WireCart): Cart {
  return {
    ...cart,
    items: cart.items.map((item) => ({
      ...item,
      productId: item.productId ?? item.id
    }))
  };
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isCurrencyCode(value: unknown): value is string {
  return typeof value === "string" && /^[A-Z]{3}$/.test(value);
}

function isInventoryStatus(value: unknown): value is InventoryStatus {
  return value === "IN_STOCK" || value === "LOW_STOCK" || value === "OUT_OF_STOCK";
}

function isSameOriginImagePath(value: unknown): value is string {
  return typeof value === "string" && (value.startsWith("/") || value.startsWith("./"));
}

// --- Formatters -------------------------------------------------------------

export function formatInventoryStatus(status: InventoryStatus): string {
  switch (status) {
    case "IN_STOCK":
      return "In stock";
    case "LOW_STOCK":
      return "Low stock";
    case "OUT_OF_STOCK":
      return "Out of stock";
  }
}

// js-cache-function-results: Intl.NumberFormat is expensive to construct, so
// cache one instance per currency rather than building a fresh formatter on
// every render (the catalog grid calls this once per product per render).
const moneyFormatters = new Map<string, Intl.NumberFormat>();

export function formatMoney(cents: number, currency: string): string {
  let formatter = moneyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat("en-US", { style: "currency", currency });
    moneyFormatters.set(currency, formatter);
  }
  return formatter.format(cents / 100);
}

// Cents → a bare 2-decimal BigDecimal string for the cart-item API body
// (4299 → "42.99"). Not a display formatter (no currency symbol/grouping) —
// it's the wire shape the backend's BigDecimal unitPrice expects.
export function formatPriceString(cents: number): string {
  return (cents / 100).toFixed(2);
}
