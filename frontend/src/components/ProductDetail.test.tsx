import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProductDetail } from "@/components/ProductDetail";
import type { CatalogProduct } from "@/lib/commerce";

// ProductDetail renders <Link to="/">, which reads router context, so even the
// "pure" props-driven test needs a RouterProvider. We mount the element under
// test at the root of a throwaway in-memory router — no real routes, no query.
// That keeps the component decoupled from the app router while still satisfying
// the <Link> context dependency.
function renderInRouter(children: ReactNode) {
  const rootRoute = createRootRoute({ component: () => children });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ["/"] })
  });
  return render(<RouterProvider router={router as never} />);
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
  return { ...actual, fetchProduct: vi.fn() };
});

import { fetchProduct } from "@/lib/commerce";
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
