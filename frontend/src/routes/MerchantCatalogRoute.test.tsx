import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory,
  createRootRouteWithContext,
  createRoute,
  createRouter
} from "@tanstack/react-router";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CatalogProduct } from "@/lib/commerce";

vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return {
    ...actual,
    createCatalogProduct: vi.fn(),
    fetchCatalogProducts: vi.fn(),
    updateCatalogProduct: vi.fn()
  };
});

import {
  createCatalogProduct,
  fetchCatalogProducts,
  updateCatalogProduct
} from "@/lib/commerce";
import { catalogQueryOptions } from "@/lib/queries";
import { Route as MerchantCatalogRoute } from "@/routes/MerchantCatalogRoute";

const products: CatalogProduct[] = [
  {
    id: "PROD0000000A1",
    sku: "SKU-MUG",
    name: "Starter Mug",
    priceCents: 1250,
    currency: "USD",
    inventoryStatus: "IN_STOCK"
  },
  {
    id: "PROD0000000B2",
    sku: "SKU-PACK",
    name: "Camp Pantry Pack",
    priceCents: 4299,
    currency: "USD",
    inventoryStatus: "LOW_STOCK"
  }
];

function renderMerchantRoute() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const rootRoute = createRootRouteWithContext<{ queryClient: QueryClient }>()();
  const route = createRoute({
    getParentRoute: () => rootRoute,
    path: "/merchant/catalog",
    loader: ({ context }) => context.queryClient.ensureQueryData(catalogQueryOptions()),
    component: MerchantCatalogRoute.options.component!,
    pendingComponent: MerchantCatalogRoute.options.pendingComponent!,
    errorComponent: MerchantCatalogRoute.options.errorComponent!
  });
  const router = createRouter({
    routeTree: rootRoute.addChildren([route]),
    context: { queryClient },
    defaultPreloadStaleTime: 0,
    history: createMemoryHistory({ initialEntries: ["/merchant/catalog"] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router as never} />
    </QueryClientProvider>
  );
}

describe("MerchantCatalogRoute", () => {
  beforeEach(() => {
    vi.mocked(fetchCatalogProducts).mockReset();
    vi.mocked(fetchCatalogProducts).mockResolvedValue(products);
    vi.mocked(createCatalogProduct).mockReset();
    vi.mocked(updateCatalogProduct).mockReset();
  });

  it("renders products and creates a product through the catalog write API", async () => {
    vi.mocked(createCatalogProduct).mockResolvedValue({
      id: "PROD0000000C3",
      sku: "SKU-NEW",
      name: "Trail Spoon",
      priceCents: 799,
      currency: "USD",
      inventoryStatus: "IN_STOCK"
    });
    renderMerchantRoute();

    expect(await screen.findByRole("heading", { name: /merchant catalog/i })).toBeInTheDocument();
    const createForm = screen.getByRole("button", { name: "Create" }).closest("form");
    expect(createForm).not.toBeNull();

    fireEvent.change(within(createForm!).getByLabelText("SKU"), { target: { value: "SKU-NEW" } });
    fireEvent.change(within(createForm!).getByLabelText("Name"), { target: { value: "Trail Spoon" } });
    fireEvent.change(within(createForm!).getByLabelText("Price"), { target: { value: "7.99" } });
    fireEvent.submit(createForm!);

    await waitFor(() => {
      expect(createCatalogProduct).toHaveBeenCalledWith({
        sku: "SKU-NEW",
        name: "Trail Spoon",
        priceCents: 799,
        inventoryStatus: "IN_STOCK"
      });
    });
    expect(await screen.findByText("Created Trail Spoon")).toBeInTheDocument();
  });

  it("updates an existing product without sending a client-supplied id", async () => {
    const existingProduct = products[0]!;
    vi.mocked(updateCatalogProduct).mockResolvedValue({
      ...existingProduct,
      name: "Updated Mug",
      priceCents: 1500,
      inventoryStatus: "LOW_STOCK"
    });
    renderMerchantRoute();

    const productName = await screen.findByDisplayValue("Starter Mug");
    const productForm = productName.closest("form");
    expect(productForm).not.toBeNull();
    fireEvent.change(productName, { target: { value: "Updated Mug" } });
    fireEvent.change(within(productForm!).getByLabelText("Price"), { target: { value: "15.00" } });
    fireEvent.change(within(productForm!).getByLabelText("Inventory"), {
      target: { value: "LOW_STOCK" }
    });
    fireEvent.submit(productForm!);

    await waitFor(() => {
      expect(updateCatalogProduct).toHaveBeenCalledWith("PROD0000000A1", {
        name: "Updated Mug",
        priceCents: 1500,
        inventoryStatus: "LOW_STOCK"
      });
    });
  });
});
