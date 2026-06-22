// TanStack Query data layer over the commerce fetchers + the BFF identity probe.
// client-swr-dedup: TanStack Query is the data layer — it dedupes in-flight
// requests, caches by key, and hands each queryFn an AbortSignal so we drop the
// manual AbortController/alive bookkeeping the old App.tsx carried.
//
// queryOptions factories are the single source of truth for each query's
// (queryKey, queryFn): route loaders prefetch with
// queryClient.ensureQueryData(<factory>()) and components read the same cache
// with useSuspenseQuery(<factory>()) / useQuery(<factory>()). No token state
// lives here — meQueryOptions wraps the browser-safe /auth/me projection and
// cartQueryOptions goes through auth.ts callApi.

import { queryOptions, useQuery, type UseQueryResult } from "@tanstack/react-query";
import { fetchMe, type User } from "@/auth";
import {
  fetchCart,
  fetchCatalogProducts,
  fetchOrder,
  fetchProduct,
  type Cart
} from "@/lib/commerce";

export const queryKeys = {
  me: ["me"] as const,
  catalog: ["catalog"] as const,
  product: (productId: string) => ["catalog", "product", productId] as const,
  cart: ["cart"] as const,
  order: (orderId: string) => ["orders", orderId] as const
};

// --- queryOptions factories ------------------------------------------------
// Each factory carries the queryKey + a signal-threading queryFn. Reuse these
// in loaders (ensureQueryData), components (useSuspenseQuery), and the thin
// hooks below — never re-spell an inline { queryKey, queryFn } config.

export function meQueryOptions() {
  return queryOptions({
    queryKey: queryKeys.me,
    queryFn: ({ signal }) => fetchMe(signal)
  });
}

export function catalogQueryOptions() {
  return queryOptions({
    queryKey: queryKeys.catalog,
    queryFn: ({ signal }) => fetchCatalogProducts(signal)
  });
}

export function productQueryOptions(productId: string) {
  return queryOptions({
    queryKey: queryKeys.product(productId),
    queryFn: ({ signal }) => fetchProduct(productId, signal)
  });
}

export function cartQueryOptions() {
  return queryOptions({
    queryKey: queryKeys.cart,
    queryFn: ({ signal }) => fetchCart(signal)
  });
}

// The order confirmation read. Mirrors productQueryOptions: the /orders/$orderId
// route loader prefetches with ensureQueryData(orderQueryOptions(id)) and the
// route component reads the same cache entry via useSuspenseQuery.
export function orderQueryOptions(orderId: string) {
  return queryOptions({
    queryKey: queryKeys.order(orderId),
    queryFn: ({ signal }) => fetchOrder(orderId, signal)
  });
}

// --- Hooks -----------------------------------------------------------------

export function useMe(): UseQueryResult<User | null> {
  return useQuery(meQueryOptions());
}

// Cart fetching is gated on an authenticated session: pass enabled=false while
// identity is loading or anonymous so we never fire /api/cart for a guest. This
// stays a dependent useQuery (not a route loader) precisely because it depends
// on the resolved /auth/me identity.
export function useCart(enabled: boolean): UseQueryResult<Cart> {
  return useQuery({ ...cartQueryOptions(), enabled });
}
