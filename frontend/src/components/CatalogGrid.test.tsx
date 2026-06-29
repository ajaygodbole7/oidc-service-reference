import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CatalogGrid, type CatalogGridProps } from "@/components/CatalogGrid";
import type { CatalogProduct } from "@/lib/commerce";

vi.mock("@/lib/commerce", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/commerce")>();
  return { ...actual, addCartItem: vi.fn() };
});

import { addCartItem } from "@/lib/commerce";

// CatalogGrid is pure: it takes the loaded products + a link renderer as props,
// so it renders without a router. Loading and error states are now route-level
// (CatalogRoute's pendingComponent / errorComponent), so this component only
// renders the loaded list (which may be empty). The route supplies the real
// typed TanStack Link; here we supply a plain anchor that encodes the target
// id, so we can assert the cell links to the product without router context.
function stubRenderLink(productId: string, children: string): ReactElement {
  return <a href={`/products/${productId}`}>{children}</a>;
}

const products: readonly CatalogProduct[] = [
  {
    id: "PROD0000000A1",
    sku: "SKU-CHAIR",
    name: "Aeron Chair",
    priceCents: 129900,
    currency: "USD",
    inventoryStatus: "IN_STOCK"
  },
  {
    id: "PROD0000000B2",
    sku: "SKU-DESK",
    name: "Standing Desk",
    priceCents: 59900,
    currency: "USD",
    inventoryStatus: "LOW_STOCK"
  }
];

const baseProps: CatalogGridProps = {
  products: [],
  renderLink: stubRenderLink
};

function renderGrid(props: CatalogGridProps) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CatalogGrid {...props} />
    </QueryClientProvider>
  );
}

describe("CatalogGrid", () => {
  beforeEach(() => {
    vi.mocked(addCartItem).mockReset();
  });

  it("renders product names and links each cell to the product route when loaded", () => {
    renderGrid({ ...baseProps, products });

    expect(screen.getByText("Aeron Chair")).toBeInTheDocument();
    expect(screen.getByText("Standing Desk")).toBeInTheDocument();
    expect(screen.getByText("2 items")).toBeInTheDocument();

    const links = screen.getAllByRole("link", { name: "View" });
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAttribute("href", "/products/PROD0000000A1");
  });

  it("renders an empty state when there are no products", () => {
    renderGrid({ ...baseProps, products: [] });

    expect(screen.getByText("No products yet")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("0 items")).toBeInTheDocument();
  });

  it("quick-adds one item from a catalog card", async () => {
    const firstProduct = products[0]!;
    vi.mocked(addCartItem).mockResolvedValue({
      id: "cart-1",
      currency: "USD",
      items: [
        {
          id: "line-1",
          productId: firstProduct.id,
          name: firstProduct.name,
          quantity: 1,
          unitPriceCents: firstProduct.priceCents,
          lineTotalCents: firstProduct.priceCents
        }
      ],
      subtotalCents: firstProduct.priceCents,
      estimatedTaxCents: 0,
      totalCents: firstProduct.priceCents
    });
    renderGrid({ ...baseProps, products });

    fireEvent.click(screen.getAllByRole("button", { name: /add/i })[0]!);

    await waitFor(() => {
      expect(addCartItem).toHaveBeenCalledWith(firstProduct.id, 1, firstProduct.priceCents);
    });
  });

  it("touches no browser token storage", () => {
    renderGrid({ ...baseProps, products });

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.body.innerHTML).not.toMatch(/Authorization|Bearer|access_token|id_token/i);
  });
});
