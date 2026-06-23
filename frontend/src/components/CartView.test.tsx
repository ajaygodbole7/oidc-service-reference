import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRoute,
  createRouter
} from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CartView } from "./CartView";
import type { Cart, CatalogProduct, Order } from "@/lib/commerce";
import { catalogQueryOptions } from "@/lib/queries";

// CartView now owns the React 19 cart-mutation + checkout Actions, so it reads
// the TanStack Query cache (useQueryClient) and navigates on checkout
// (useNavigate). Each render is wrapped in a throwaway in-memory router + a
// fresh QueryClient. The commerce mutations (placeOrder, removeCartItem) are
// mocked, and navigation is asserted through the router's resolved location —
// no real network, no token state. The token boundary is never exercised here.

vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return {
    ...actual,
    placeOrder: vi.fn(),
    removeCartItem: vi.fn(),
    // CartItemList reads the catalog (useQuery) to resolve line names; default to an empty
    // catalog so tests that don't seed it fall back to the item's own name without a real fetch.
    fetchCatalogProducts: vi.fn(() => Promise.resolve([] as CatalogProduct[]))
  };
});

import { placeOrder, removeCartItem } from "@/lib/commerce";

const SAMPLE_CART: Cart = {
  id: "current",
  currency: "USD",
  items: [
    { id: "i1", name: "Filter Coffee", quantity: 2, unitPriceCents: 1250, lineTotalCents: 2500 },
    { id: "i2", name: "Ceramic Mug", quantity: 1, unitPriceCents: 1800, lineTotalCents: 1800 }
  ],
  subtotalCents: 4300,
  estimatedTaxCents: 344,
  totalCents: 4644
};

// Mount the CartView under a real (in-memory) router so useNavigate resolves,
// plus a fresh QueryClient so the mutation Actions' setQueryData/invalidate
// calls land somewhere. The router exposes its current location for assertions.
function renderCart(node: ReactNode, seedCatalog?: readonly CatalogProduct[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  // Seed the catalog cache so CartItemList's useQuery resolves line names synchronously
  // (cache hit, no fetch) when a test exercises the catalog-name join.
  if (seedCatalog) {
    queryClient.setQueryData(catalogQueryOptions().queryKey, seedCatalog);
  }
  const rootRoute = createRootRoute({ component: () => node });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ["/cart"] })
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
  return { ...result, router };
}

describe("CartView", () => {
  beforeEach(() => {
    vi.mocked(placeOrder).mockReset();
    vi.mocked(removeCartItem).mockReset();
  });

  it("anonymous: renders the sign-in prompt with a return_to login link", async () => {
    renderCart(<CartView authenticated={false} cart={undefined} status="pending" error={null} />);

    // loginHref() derives return_to from window.location (jsdom default "/"),
    // not from the in-memory router used to satisfy useNavigate.
    const link = await screen.findByTestId("sign-in-link");
    expect(link).toHaveAttribute(
      "href",
      `/auth/login?return_to=${encodeURIComponent("/")}`
    );
    // No cart UI for a guest.
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("list", { name: /cart items/i })).not.toBeInTheDocument();
  });

  it("authenticated + pending: renders skeletons, not the sign-in prompt", async () => {
    const { container } = renderCart(
      <CartView authenticated cart={undefined} status="pending" error={null} />
    );

    await waitFor(() => {
      expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0);
    });
    expect(screen.queryByTestId("sign-in-link")).not.toBeInTheDocument();
  });

  it("authenticated + error: renders an alert with a message", async () => {
    renderCart(
      <CartView authenticated cart={undefined} status="error" error={new Error("boom")} />
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent(/cart/i);
  });

  it("authenticated + empty cart: renders the empty-cart message and no checkout", async () => {
    const emptyCart: Cart = {
      id: "current",
      currency: "USD",
      items: [],
      subtotalCents: 0,
      estimatedTaxCents: 0,
      totalCents: 0
    };

    renderCart(<CartView authenticated cart={emptyCart} status="success" error={null} />);

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: /cart items/i })).not.toBeInTheDocument();
    // Checkout is hidden when the cart is empty.
    expect(screen.queryByRole("button", { name: /place order/i })).not.toBeInTheDocument();
  });

  it("authenticated + loaded: resolves each line's display name from the catalog by product id", async () => {
    // The cart line's id IS the product id (CartResponse.Item echoes productId as both id and
    // name). CartItemList joins it against the catalog to show the real product name instead of
    // the id echo. Seed the catalog so the join resolves synchronously.
    const cart: Cart = {
      id: "current",
      currency: "USD",
      items: [
        { id: "PROD-1", name: "PROD-1", quantity: 1, unitPriceCents: 1250, lineTotalCents: 1250 }
      ],
      subtotalCents: 1250,
      estimatedTaxCents: 100,
      totalCents: 1350
    };
    const catalog: CatalogProduct[] = [
      {
        id: "PROD-1",
        name: "Starter Mug",
        currency: "USD",
        priceCents: 1250,
        inventoryStatus: "IN_STOCK"
      }
    ];

    renderCart(<CartView authenticated cart={cart} status="success" error={null} />, catalog);

    const list = await screen.findByRole("list", { name: /cart items/i });
    // The resolved catalog name is shown, not the product-id echo.
    expect(within(list).getByText("Starter Mug")).toBeInTheDocument();
    expect(within(list).queryByText("PROD-1")).not.toBeInTheDocument();
  });

  it("authenticated + loaded: renders each line item and the money summary", async () => {
    renderCart(<CartView authenticated cart={SAMPLE_CART} status="success" error={null} />);

    // Items live in a labelled list.
    const list = await screen.findByRole("list", { name: /cart items/i });
    const items = within(list).getAllByRole("listitem");
    expect(items).toHaveLength(2);

    expect(screen.getByText("Filter Coffee")).toBeInTheDocument();
    expect(screen.getByText("Ceramic Mug")).toBeInTheDocument();

    // qty · unit price and line total are formatted via formatMoney.
    expect(within(items[0]!).getByText(/2\s*·\s*\$12\.50/)).toBeInTheDocument();
    expect(within(items[0]!).getByText("$25.00")).toBeInTheDocument();

    // Summary is a description list with the three money rows.
    const summary = screen.getByRole("group", { name: /order summary/i });
    expect(within(summary).getByText("$43.00")).toBeInTheDocument(); // subtotal
    expect(within(summary).getByText("$3.44")).toBeInTheDocument(); // estimated tax
    expect(within(summary).getByText("$46.44")).toBeInTheDocument(); // total
  });

  it("checkout Action: mints an Idempotency-Key, calls placeOrder, navigates to /orders/<id>", async () => {
    const order: Order = {
      id: "ord-001",
      status: "CONFIRMED",
      sourceCartId: "current",
      currency: "USD",
      totalCents: 4644,
      createdAt: "2026-06-22T12:00:00Z",
      lines: [
        { productId: "p1", name: "Filter Coffee", quantity: 2, unitPriceCents: 1250, lineTotalCents: 2500 }
      ]
    };
    vi.mocked(placeOrder).mockResolvedValue(order);

    const { router } = renderCart(
      <CartView authenticated cart={SAMPLE_CART} status="success" error={null} />
    );

    // Wait for the in-memory router to resolve the initial render.
    await screen.findByLabelText(/postal code/i);

    fireEvent.change(screen.getByLabelText(/postal code/i), { target: { value: "94105" } });
    fireEvent.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => {
      expect(placeOrder).toHaveBeenCalledTimes(1);
    });

    // placeOrder is called with the command + a minted Idempotency-Key (a UUID).
    const [command, idempotencyKey] = vi.mocked(placeOrder).mock.calls[0]!;
    expect(command).toEqual({ paymentMethodId: "pm-card-1", shippingPostalCode: "94105" });
    expect(idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );

    // The Action navigates to the order confirmation route.
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/orders/ord-001");
    });
  });

  it("Remove control calls removeCartItem with the line product id", async () => {
    vi.mocked(removeCartItem).mockResolvedValue({
      ...SAMPLE_CART,
      items: [SAMPLE_CART.items[1]!]
    });

    renderCart(<CartView authenticated cart={SAMPLE_CART} status="success" error={null} />);

    const list = await screen.findByRole("list", { name: /cart items/i });
    const firstItem = within(list).getAllByRole("listitem")[0]!;
    fireEvent.click(within(firstItem).getByRole("button", { name: /remove/i }));

    await waitFor(() => {
      expect(removeCartItem).toHaveBeenCalledWith("i1");
    });
  });
});
