import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProductDetail } from "@/components/ProductDetail";
import type { Cart, CatalogProduct } from "@/lib/commerce";

// ProductDetail renders <Link to="/"> (router context) and an Add-to-cart
// Action that reads the TanStack Query cache (useQueryClient), so the props-
// driven test needs BOTH a RouterProvider and a QueryClientProvider. We mount
// the element under test at the root of a throwaway in-memory router — no real
// routes — wrapped in a fresh QueryClient.
function renderInRouter(children: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const rootRoute = createRootRoute({ component: () => children });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ["/"] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

const sampleProduct: CatalogProduct = {
  id: "01HZQ8ABCDEFG",
  sku: "SKU-CHAIR",
  name: "Aeron Chair",
  description: "An ergonomic office chair with adjustable lumbar support.",
  priceCents: 129900,
  currency: "USD",
  inventoryStatus: "LOW_STOCK"
};

describe("ProductDetail (pure presentational)", () => {
  beforeEach(() => {
    vi.mocked(addCartItem).mockReset();
  });

  it("renders name, price, description, inventory, and a back link from the loaded product", async () => {
    renderInRouter(<ProductDetail product={sampleProduct} />);

    expect(await screen.findByText("Aeron Chair")).toBeInTheDocument();
    expect(screen.getByText("$1,299.00")).toBeInTheDocument();
    expect(
      screen.getByText("An ergonomic office chair with adjustable lumbar support.")
    ).toBeInTheDocument();
    expect(screen.getByText("Low stock")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /back to catalog/i })).toHaveAttribute("href", "/");
  });

  it("Add-to-cart Action posts the product + quantity through addCartItem", async () => {
    const addedCart: Cart = {
      id: "cart-1",
      currency: "USD",
      items: [
        {
          id: "line-1",
          productId: sampleProduct.id,
          name: "Aeron Chair",
          quantity: 2,
          unitPriceCents: 129900,
          lineTotalCents: 259800
        }
      ],
      subtotalCents: 259800,
      estimatedTaxCents: 0,
      totalCents: 259800
    };
    vi.mocked(addCartItem).mockResolvedValue(addedCart);

    renderInRouter(<ProductDetail product={sampleProduct} />);

    // Wait for the in-memory router to resolve the initial render.
    await screen.findByText("Aeron Chair");

    // Bump the quantity stepper to 2, then submit the Action.
    fireEvent.click(screen.getByRole("button", { name: /increase quantity/i }));
    fireEvent.click(screen.getByRole("button", { name: /add to cart/i }));

    // The Action calls addCartItem with (productId, quantity, unitPriceCents).
    await waitFor(() => {
      expect(addCartItem).toHaveBeenCalledWith(sampleProduct.id, 2, sampleProduct.priceCents);
    });
    // The button reverts out of its optimistic pending affordance once settled.
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /add to cart/i })).toBeEnabled();
    });
  });
});

// Route-component test: drive the real ProductRoute (loader → useSuspenseQuery)
// by mocking the fetcher. Proves the loader-prefetch → suspense-read path and
// the 404 → errorComponent path without hitting the network. The loader lives
// on the non-lazy route, so we rebuild a route tree here that mirrors
// src/router.tsx: a context root carrying the queryClient, the loader calling
// ensureQueryData(productQueryOptions(id)), and the lazy Route's
// component/pendingComponent/errorComponent.
vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return { ...actual, fetchProduct: vi.fn(), addCartItem: vi.fn() };
});

import { addCartItem, fetchProduct } from "@/lib/commerce";
import { productQueryOptions } from "@/lib/queries";
import { Route as ProductRoute } from "@/routes/ProductRoute";

function renderProductRoute(productId: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const rootRoute = createRootRouteWithContext<{ queryClient: QueryClient }>()();
  const productRoute = createRoute({
    getParentRoute: () => rootRoute,
    path: "/products/$productId",
    loader: ({ context, params }) =>
      context.queryClient.ensureQueryData(productQueryOptions(params.productId)),
    // ProductRoute is a lazy route, so its options are RouteComponent | undefined;
    // they are always defined at runtime. Assert non-null for exactOptionalPropertyTypes.
    component: ProductRoute.options.component!,
    pendingComponent: ProductRoute.options.pendingComponent!,
    errorComponent: ProductRoute.options.errorComponent!
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([productRoute]),
    context: { queryClient },
    defaultPreloadStaleTime: 0,
    history: createMemoryHistory({ initialEntries: [`/products/${productId}`] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

describe("ProductRoute (loader prefetch → useSuspenseQuery)", () => {
  beforeEach(() => {
    vi.mocked(fetchProduct).mockReset();
  });

  it("renders the loaded product after the loader prefetches it", async () => {
    vi.mocked(fetchProduct).mockResolvedValue(sampleProduct);

    renderProductRoute(sampleProduct.id);

    expect(await screen.findByText("Aeron Chair")).toBeInTheDocument();
    expect(screen.getByText("$1,299.00")).toBeInTheDocument();
  });

  it("renders the not-found alert when fetchProduct rejects (404)", async () => {
    vi.mocked(fetchProduct).mockRejectedValue(new Error("Product request failed (404)"));

    renderProductRoute("missing");

    expect(await screen.findByRole("alert")).toHaveTextContent(/not found|unavailable/i);
  });
});
