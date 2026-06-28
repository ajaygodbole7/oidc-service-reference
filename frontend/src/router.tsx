// Code-based TanStack Router (not file-based — no codegen plugin, for build
// predictability). The root renders the AppShell (header + nav + <Outlet/>);
// three child routes hang off it. Each route component is code-split via
// .lazy() so the per-screen bundles load on navigation, keeping the initial
// chunk small (the screen agents fill the lazy bodies in src/routes/*).

import { QueryClient } from "@tanstack/react-query";
import {
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import {
  catalogQueryOptions,
  orderQueryOptions,
  ordersQueryOptions,
  productQueryOptions
} from "@/lib/queries";

export interface RouterContext {
  readonly queryClient: QueryClient;
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: AppShell
});

// Loaders live on the critical (non-lazy) route config so the catalog/product
// data is prefetched into the Query cache before the component renders; the
// component bodies (+ pending/error UI) stay code-split via .lazy(). Each
// loader calls ensureQueryData with the SAME queryOptions the component reads
// via useSuspenseQuery, so the prefetch and the read share one cache entry.
const catalogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  loader: ({ context }) => context.queryClient.ensureQueryData(catalogQueryOptions())
}).lazy(() => import("@/routes/CatalogRoute").then((d) => d.Route));

const productRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/products/$productId",
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(productQueryOptions(params.productId))
}).lazy(() => import("@/routes/ProductRoute").then((d) => d.Route));

const cartRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/cart"
}).lazy(() => import("@/routes/CartRoute").then((d) => d.Route));

const merchantCatalogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/merchant/catalog",
  loader: ({ context }) => context.queryClient.ensureQueryData(catalogQueryOptions())
}).lazy(() => import("@/routes/MerchantCatalogRoute").then((d) => d.Route));

const ordersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/orders",
  loader: ({ context }) => context.queryClient.ensureQueryData(ordersQueryOptions())
}).lazy(() => import("@/routes/OrderHistoryRoute").then((d) => d.Route));

const orderRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/orders/$orderId",
  loader: ({ context, params }) =>
    context.queryClient.ensureQueryData(orderQueryOptions(params.orderId))
}).lazy(() => import("@/routes/OrderRoute").then((d) => d.Route));

const routeTree = rootRoute.addChildren([
  catalogRoute,
  productRoute,
  cartRoute,
  merchantCatalogRoute,
  ordersRoute,
  orderRoute
]);

export function createAppRouter(queryClient: QueryClient) {
  return createRouter({
    routeTree,
    context: { queryClient },
    defaultPreload: "intent",
    // CRITICAL for the TanStack Query integration: tell Router never to treat
    // its own preload cache as fresh, so it always defers to Query's cache for
    // freshness. Without this, Router's preload cache would shadow Query's
    // staleTime and a preloaded route could render data Query considers stale.
    defaultPreloadStaleTime: 0
  });
}

// Register the router instance type for end-to-end type inference on Link/params.
declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
}
