// Commerce data layer: types, same-origin BFF fetchers, runtime validators, and
// display formatters for the catalog + cart. Moved out of App.tsx so screen
// components and the TanStack Query hooks (src/lib/queries.ts) share one source
// of truth. No tokens, no auth state — fetchers call same-origin /api/** only
// (catalog is anonymous; cart goes through auth.ts callApi for CSRF + 401
// handling). The token boundary stays in auth.ts.

import { callApi } from "@/auth";

export type CartItem = {
  readonly id: string;
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

export type InventoryStatus = "in_stock" | "low_stock" | "out_of_stock";

export type CatalogProduct = {
  readonly id: string;
  readonly name: string;
  readonly description: string;
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
  if (!isCart(body)) throw new Error("Cart response had an unexpected shape");
  return body;
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
    typeof product.name === "string" &&
    typeof product.description === "string" &&
    isNonNegativeInteger(product.priceCents) &&
    isCurrencyCode(product.currency) &&
    isInventoryStatus(product.inventoryStatus) &&
    (product.imageUrl === undefined || isSameOriginImagePath(product.imageUrl))
  );
}

export function isCart(value: unknown): value is Cart {
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

function isCartItem(value: unknown): value is CartItem {
  if (value === null || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return (
    typeof item.id === "string" &&
    typeof item.name === "string" &&
    isNonNegativeInteger(item.quantity) &&
    isNonNegativeInteger(item.unitPriceCents) &&
    isNonNegativeInteger(item.lineTotalCents)
  );
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isCurrencyCode(value: unknown): value is string {
  return typeof value === "string" && /^[A-Z]{3}$/.test(value);
}

function isInventoryStatus(value: unknown): value is InventoryStatus {
  return value === "in_stock" || value === "low_stock" || value === "out_of_stock";
}

function isSameOriginImagePath(value: unknown): value is string {
  return typeof value === "string" && (value.startsWith("/") || value.startsWith("./"));
}

// --- Formatters -------------------------------------------------------------

export function formatInventoryStatus(status: InventoryStatus): string {
  switch (status) {
    case "in_stock":
      return "In stock";
    case "low_stock":
      return "Low stock";
    case "out_of_stock":
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
