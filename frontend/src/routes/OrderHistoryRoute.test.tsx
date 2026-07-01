import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CatalogProduct, Order } from "@/lib/commerce";

vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return { ...actual, fetchCatalogProducts: vi.fn(), fetchOrders: vi.fn() };
});

import { fetchCatalogProducts, fetchOrders } from "@/lib/commerce";
import { catalogQueryOptions, ordersQueryOptions } from "@/lib/queries";
import { Route as OrderHistoryRoute } from "@/routes/OrderHistoryRoute";

const orders: Order[] = [
  {
    id: "ord-001",
    status: "CONFIRMED",
    sourceCartId: "cart-alice",
    currency: "USD",
    totalCents: 4299,
    createdAt: "2026-06-22T12:00:00Z",
    lines: [
      { productId: "prod-pack", quantity: 1, unitPriceCents: 4299, lineTotalCents: 4299 }
    ]
  }
];

const catalogProducts: CatalogProduct[] = [
  {
    id: "prod-pack",
    name: "Camp Pantry Pack",
    currency: "USD",
    priceCents: 4299,
    inventoryStatus: "IN_STOCK"
  }
];

function renderOrderHistoryRoute() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const rootRoute = createRootRouteWithContext<{ queryClient: QueryClient }>()();
  const route = createRoute({
    getParentRoute: () => rootRoute,
    path: "/orders",
    loader: ({ context }) =>
      Promise.all([
        context.queryClient.ensureQueryData(ordersQueryOptions()),
        context.queryClient.ensureQueryData(catalogQueryOptions())
      ]),
    component: OrderHistoryRoute.options.component!,
    pendingComponent: OrderHistoryRoute.options.pendingComponent!,
    errorComponent: OrderHistoryRoute.options.errorComponent!
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([route]),
    context: { queryClient },
    defaultPreloadStaleTime: 0,
    history: createMemoryHistory({ initialEntries: ["/orders"] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

describe("OrderHistoryRoute", () => {
  beforeEach(() => {
    vi.mocked(fetchCatalogProducts).mockReset();
    vi.mocked(fetchCatalogProducts).mockResolvedValue(catalogProducts);
    vi.mocked(fetchOrders).mockReset();
  });

  it("renders current user's orders with catalog-resolved line names", async () => {
    vi.mocked(fetchOrders).mockResolvedValue({ items: orders, nextCursor: "next-page" });

    renderOrderHistoryRoute();

    expect(await screen.findByRole("heading", { name: "Orders" })).toBeInTheDocument();
    expect(screen.getByText("ord-001")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMED")).toBeInTheDocument();
    expect(screen.getAllByText("$42.99")).not.toHaveLength(0);
    expect(await screen.findByText("Camp Pantry Pack")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /load more orders/i })).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.queryByText("prod-pack")).not.toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /view order/i })).toHaveAttribute("href", "/orders/ord-001");
  });

  it("renders an empty order state", async () => {
    vi.mocked(fetchOrders).mockResolvedValue({ items: [] });

    renderOrderHistoryRoute();

    expect(await screen.findByText("No orders yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /browse catalog/i })).toHaveAttribute("href", "/");
  });

  it("shows empty state and load-more button when initial page is empty with cursor", async () => {
    vi.mocked(fetchOrders)
      .mockResolvedValueOnce({ items: [], nextCursor: "next-page" })
      .mockResolvedValueOnce({ items: orders });

    renderOrderHistoryRoute();

    expect(await screen.findByText("No orders yet")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /load more orders/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /load more orders/i }));

    expect(await screen.findByText("ord-001")).toBeInTheDocument();
    expect(vi.mocked(fetchOrders)).toHaveBeenLastCalledWith(expect.any(AbortSignal), "next-page");
  });

  it("shows an error alert when loading more orders fails", async () => {
    vi.mocked(fetchOrders)
      .mockResolvedValueOnce({ items: orders, nextCursor: "next-page" })
      .mockRejectedValueOnce(new Error("Network error"));

    renderOrderHistoryRoute();

    expect(await screen.findByText("ord-001")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /load more orders/i }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Network error");
    expect(screen.getByRole("button", { name: /load more orders/i })).not.toBeDisabled();
  });
});
