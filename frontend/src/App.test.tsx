import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

// URL-keyed mock router so tests don't depend on the order the component
// happens to fire requests in. Each handler is responsible for one route;
// unknown URLs throw loudly so we never silently fall through to a real
// network call.
function setupFetch(routes: Record<string, () => Response | Promise<Response>>) {
  return vi.spyOn(global, "fetch").mockImplementation(async (input) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.href
          : input.url;
    const handler = routes[url];
    if (!handler) throw new Error(`unhandled fetch: ${url}`);
    return handler();
  });
}

const aliceClaims = {
  sub: "alice-123",
  preferred_username: "alice",
  roles: ["user"]
};

const cartFixture = {
  id: "cart-alice",
  currency: "USD",
  items: [
    {
      id: "line-1",
      name: "Trail Coffee",
      quantity: 2,
      unitPriceCents: 1299,
      lineTotalCents: 2598
    },
    {
      id: "line-2",
      name: "Insulated Mug",
      quantity: 1,
      unitPriceCents: 2400,
      lineTotalCents: 2400
    }
  ],
  subtotalCents: 4998,
  estimatedTaxCents: 412,
  totalCents: 5410
};

const catalogFixture = {
  products: [
    {
      id: "prod-coffee",
      name: "Trail Blend",
      description: "Small-batch beans roasted for early starts and late deploys.",
      priceCents: 1299,
      currency: "USD",
      inventoryStatus: "in_stock"
    },
    {
      id: "prod-mug",
      name: "Market Tote",
      description: "Keeps coffee hot through the whole checkout path.",
      priceCents: 2400,
      currency: "USD",
      inventoryStatus: "low_stock"
    }
  ]
};

describe("App", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while identity is loading", () => {
    setupFetch({
      "/auth/me": () => new Promise<Response>(() => undefined),
      "/api/catalog/products": () => new Promise<Response>(() => undefined)
    });

    render(<App />);

    expect(screen.getByText(/checking session/i)).toBeInTheDocument();
  });

  it("renders anonymous catalog browsing and the sign-in entry when /auth/me returns 401", async () => {
    setupFetch({
      "/auth/me": () => new Response(null, { status: 401 }),
      "/api/catalog/products": () => Response.json(catalogFixture)
    });

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Catalog" })).toBeInTheDocument();
    expect(await screen.findByText("Trail Blend")).toBeInTheDocument();
    expect(screen.getByText("Market Tote")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /sign in/i })).toBeInTheDocument();
    // Per return-to-login contract: a bare `/auth/login` link is forbidden.
    // The Sign in link must include `return_to=<current route>`. jsdom's
    // default location yields pathname "/", so the encoded value is "%2F".
    expect(screen.getByTestId("sign-in-link")).toHaveAttribute(
      "href",
      `/auth/login?return_to=${encodeURIComponent("/")}`
    );
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("renders a loaded cart from same-origin /api/cart", async () => {
    const fetchSpy = setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
      "/api/catalog/products": () => Response.json(catalogFixture),
      "/api/cart": () => Response.json(cartFixture)
    });

    render(<App />);

    expect(await screen.findByText(/signed in as/i)).toBeInTheDocument();
    expect(await screen.findByText("Trail Blend")).toBeInTheDocument();
    expect(screen.getByText("Insulated Mug")).toBeInTheDocument();
    expect(screen.getByText("Subtotal")).toBeInTheDocument();
    expect(screen.getByText("$54.10")).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/catalog/products",
      expect.objectContaining({
        credentials: "include",
        headers: { Accept: "application/json" },
        signal: expect.any(AbortSignal) as AbortSignal
      })
    );
    expect(fetchSpy).toHaveBeenCalledWith(
      "/api/cart",
      expect.objectContaining({
        credentials: "include",
        headers: { Accept: "application/json" },
        signal: expect.any(AbortSignal) as AbortSignal
      })
    );
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("renders the empty cart state", async () => {
    setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
      "/api/catalog/products": () => Response.json(catalogFixture),
      "/api/cart": () =>
        Response.json({
          id: "cart-alice",
          currency: "USD",
          items: [],
          subtotalCents: 0,
          estimatedTaxCents: 0,
          totalCents: 0
        })
    });

    render(<App />);

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
  });

  it("treats a missing current cart as empty", async () => {
    setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
      "/api/catalog/products": () => Response.json(catalogFixture),
      "/api/cart": () => new Response(null, { status: 404 })
    });

    render(<App />);

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
  });

  it("rejects malformed cart money values", async () => {
    setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
      "/api/catalog/products": () => Response.json(catalogFixture),
      "/api/cart": () =>
        Response.json({
          ...cartFixture,
          totalCents: 5410.5
        })
    });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cart response had an unexpected shape"
    );
  });

  it("renders cart errors honestly", async () => {
    setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
      "/api/catalog/products": () => Response.json(catalogFixture),
      "/api/cart": () => new Response(null, { status: 500 })
    });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cart request failed (500)"
    );
  });

  it("renders catalog errors without navigating or exposing browser tokens", async () => {
    setupFetch({
      "/auth/me": () => new Response(null, { status: 401 }),
      "/api/catalog/products": () => new Response(null, { status: 503 })
    });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Catalog request failed (503)"
    );
    expect(screen.getByRole("link", { name: /sign in/i })).toBeInTheDocument();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("rejects malformed catalog prices", async () => {
    setupFetch({
      "/auth/me": () => new Response(null, { status: 401 }),
      "/api/catalog/products": () =>
        Response.json({
          products: [
            {
              ...catalogFixture.products[0],
              priceCents: 1299.5
            }
          ]
        })
    });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Catalog response had an unexpected shape"
    );
  });
});
