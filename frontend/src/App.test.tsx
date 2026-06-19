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

describe("App", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while identity is loading", () => {
    setupFetch({
      "/auth/me": () => new Promise<Response>(() => undefined)
    });

    render(<App />);

    expect(screen.getByText(/loading cart/i)).toBeInTheDocument();
  });

  it("renders the anonymous sign-in entry when /auth/me returns 401", async () => {
    setupFetch({
      "/auth/me": () => new Response(null, { status: 401 })
    });

    render(<App />);

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
      "/api/cart": () => Response.json(cartFixture)
    });

    render(<App />);

    expect(await screen.findByText(/signed in as/i)).toBeInTheDocument();
    expect(await screen.findByText("Trail Coffee")).toBeInTheDocument();
    expect(screen.getByText("Insulated Mug")).toBeInTheDocument();
    expect(screen.getByText("Subtotal")).toBeInTheDocument();
    expect(screen.getByText("$54.10")).toBeInTheDocument();
    expect(fetchSpy).toHaveBeenLastCalledWith(
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
      "/api/cart": () => new Response(null, { status: 404 })
    });

    render(<App />);

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
  });

  it("rejects malformed cart money values", async () => {
    setupFetch({
      "/auth/me": () => Response.json(aliceClaims),
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
      "/api/cart": () => new Response(null, { status: 500 })
    });

    render(<App />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cart request failed (500)"
    );
  });
});
