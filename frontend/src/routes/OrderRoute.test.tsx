import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CatalogProduct, Order } from "@/lib/commerce";

// Route-component test: drive the real OrderRoute (loader → useSuspenseQuery) by
// mocking fetchOrder. Proves the loader-prefetch → suspense-read confirmation
// path and the 404 → errorComponent path without hitting the network. Mirrors
// the ProductRoute test: a context root carrying the queryClient, the loader
// calling ensureQueryData(orderQueryOptions(id)), and the lazy Route's
// component/pendingComponent/errorComponent.
vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return { ...actual, fetchCatalogProducts: vi.fn(), fetchOrder: vi.fn() };
});

import { fetchCatalogProducts, fetchOrder } from "@/lib/commerce";
import { orderQueryOptions } from "@/lib/queries";
import { Route as OrderRoute } from "@/routes/OrderRoute";

const sampleOrder: Order = {
  id: "ord-001",
  status: "CONFIRMED",
  sourceCartId: "cart-alice",
  currency: "USD",
  totalCents: 4653,
  createdAt: "2026-06-22T12:00:00Z",
  lines: [
    { productId: "prod-pack", quantity: 1, unitPriceCents: 4299, lineTotalCents: 4299 }
  ]
};

const catalogProducts: CatalogProduct[] = [
  {
    id: "prod-pack",
    name: "Camp Pantry Pack",
    currency: "USD",
    priceCents: 4299,
    inventoryStatus: "IN_STOCK"
  }
];

function renderOrderRoute(orderId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const rootRoute = createRootRouteWithContext<{ queryClient: QueryClient }>()();
  const orderRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/orders/$orderId",
    loader: ({ context, params }) =>
      context.queryClient.ensureQueryData(orderQueryOptions(params.orderId)),
    component: OrderRoute.options.component!,
    pendingComponent: OrderRoute.options.pendingComponent!,
    errorComponent: OrderRoute.options.errorComponent!
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([orderRoute]),
    context: { queryClient },
    defaultPreloadStaleTime: 0,
    history: createMemoryHistory({ initialEntries: [`/orders/${orderId}`] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

describe("OrderRoute (confirmation from a loaded order)", () => {
  beforeEach(() => {
    vi.mocked(fetchCatalogProducts).mockReset();
    vi.mocked(fetchCatalogProducts).mockResolvedValue(catalogProducts);
    vi.mocked(fetchOrder).mockReset();
  });

  it("renders the order-placed heading, id, status, total, and lines", async () => {
    vi.mocked(fetchOrder).mockResolvedValue(sampleOrder);

    renderOrderRoute(sampleOrder.id);

    expect(await screen.findByRole("heading", { name: /order placed/i })).toBeInTheDocument();
    expect(screen.getByText("ord-001")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText("$46.53")).toBeInTheDocument(); // total
    expect(screen.getByText("Camp Pantry Pack")).toBeInTheDocument();
    expect(screen.queryByText("prod-pack")).not.toBeInTheDocument();
  });

  it("renders the not-found alert when fetchOrder rejects (404)", async () => {
    vi.mocked(fetchOrder).mockRejectedValue(new Error("Order request failed (404)"));

    renderOrderRoute("missing");

    expect(await screen.findByRole("alert")).toHaveTextContent(/not found|unavailable/i);
  });
});
