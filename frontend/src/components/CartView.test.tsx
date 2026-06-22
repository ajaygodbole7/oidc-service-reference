import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CartView } from "./CartView";
import type { Cart } from "@/lib/commerce";

// CartView is a pure presentational component: it takes the already-derived
// auth + cart state as props, so it renders without the router or a
// QueryClient. Each test feeds one branch of the state machine and asserts the
// visible contract. The token boundary is never exercised here — there is no
// fetch, no storage, no Authorization header; CartView only receives data.

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

describe("CartView", () => {
  it("anonymous: renders the sign-in prompt with a return_to login link", () => {
    render(<CartView authenticated={false} cart={undefined} status="pending" error={null} />);

    const link = screen.getByTestId("sign-in-link");
    expect(link).toHaveAttribute(
      "href",
      `/auth/login?return_to=${encodeURIComponent("/")}`
    );
    // No cart UI for a guest.
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByRole("list", { name: /cart items/i })).not.toBeInTheDocument();
  });

  it("authenticated + pending: renders skeletons, not the sign-in prompt", () => {
    const { container } = render(
      <CartView authenticated cart={undefined} status="pending" error={null} />
    );

    expect(screen.queryByTestId("sign-in-link")).not.toBeInTheDocument();
    expect(container.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0);
  });

  it("authenticated + error: renders an alert with a message", () => {
    render(
      <CartView
        authenticated
        cart={undefined}
        status="error"
        error={new Error("boom")}
      />
    );

    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent(/cart/i);
  });

  it("authenticated + empty cart: renders the empty-cart message", () => {
    const emptyCart: Cart = {
      id: "current",
      currency: "USD",
      items: [],
      subtotalCents: 0,
      estimatedTaxCents: 0,
      totalCents: 0
    };

    render(<CartView authenticated cart={emptyCart} status="success" error={null} />);

    expect(screen.getByText(/your cart is empty/i)).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: /cart items/i })).not.toBeInTheDocument();
  });

  it("authenticated + loaded: renders each line item and the money summary", () => {
    render(<CartView authenticated cart={SAMPLE_CART} status="success" error={null} />);

    // Items live in a labelled list.
    const list = screen.getByRole("list", { name: /cart items/i });
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
});
