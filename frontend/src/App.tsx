import { useEffect, useMemo, useState } from "react";
import { callApi, fetchMe, loginHref, signOut, type User } from "./auth";

type LoadState = "loading" | "anonymous" | "authenticated";
type CartState =
  | { readonly status: "idle" }
  | { readonly status: "loading" }
  | { readonly status: "loaded"; readonly cart: Cart }
  | { readonly status: "empty"; readonly cart: Cart }
  | { readonly status: "error"; readonly message: string };

type CartItem = {
  readonly id: string;
  readonly name: string;
  readonly quantity: number;
  readonly unitPriceCents: number;
  readonly lineTotalCents: number;
};

type Cart = {
  readonly id: string;
  readonly currency: string;
  readonly items: readonly CartItem[];
  readonly subtotalCents: number;
  readonly estimatedTaxCents: number;
  readonly totalCents: number;
};

export function App() {
  const [state, setState] = useState<LoadState>("loading");
  const [user, setUser] = useState<User | null>(null);
  const [cartState, setCartState] = useState<CartState>({ status: "idle" });
  // Captures the route the user was on when login became required so
  // the callback can replay it. Computed once on mount: loginHref()
  // reads window.location, which is stable for this SPA (no client-side
  // router), and the anonymous panel only renders for state="anonymous"
  // — by then the URL has not changed since mount.
  const signInHref = useMemo(() => loginHref(), []);

  useEffect(() => {
    const controller = new AbortController();
    let alive = true;
    fetchMe(controller.signal)
      .then((u) => {
        if (!alive) return;
        if (u) {
          setUser(u);
          setState("authenticated");
        } else {
          setState("anonymous");
        }
      })
      .catch(() => {
        if (alive) setState("anonymous");
      });
    return () => {
      alive = false;
      controller.abort();
    };
  }, []);

  useEffect(() => {
    if (state !== "authenticated") {
      setCartState({ status: "idle" });
      return;
    }

    const controller = new AbortController();
    let alive = true;
    setCartState({ status: "loading" });

    fetchCart(controller.signal)
      .then((cart) => {
        if (!alive) return;
        setCartState({
          status: cart.items.length === 0 ? "empty" : "loaded",
          cart
        });
      })
      .catch((error: unknown) => {
        if (!alive || controller.signal.aborted) return;
        setCartState({
          status: "error",
          message: error instanceof Error ? error.message : "Cart is unavailable"
        });
      });

    return () => {
      alive = false;
      controller.abort();
    };
  }, [state]);

  return (
    <main className="app-shell">
      <section className="commerce-panel">
        <header className="commerce-header">
          <div>
            <p className="eyebrow">oidc-service-reference</p>
            <h1>Cart</h1>
          </div>

          {state === "authenticated" && user && (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                void signOut();
              }}
            >
              <button type="submit">Sign out</button>
            </form>
          )}
        </header>

        {state === "loading" && <p className="muted">Loading cart…</p>}

        {state === "anonymous" && (
          <div className="cart-state">
            <h2>Your cart is waiting</h2>
            <p>Sign in to view saved items and checkout.</p>
            <a className="button-link" href={signInHref} data-testid="sign-in-link">
              Sign in
            </a>
          </div>
        )}

        {state === "authenticated" && user && (
          <>
            <p className="muted">
              Signed in as <strong>{user.preferred_username ?? user.sub}</strong>
            </p>
            <CartPanel state={cartState} />
          </>
        )}
      </section>
    </main>
  );
}

function CartPanel({ state }: { readonly state: CartState }) {
  if (state.status === "idle" || state.status === "loading") {
    return <p className="muted">Loading cart…</p>;
  }

  if (state.status === "error") {
    return (
      <div className="cart-state" role="alert">
        <h2>Cart unavailable</h2>
        <p>{state.message}</p>
      </div>
    );
  }

  if (state.status === "empty") {
    return (
      <div className="cart-state">
        <h2>Your cart is empty</h2>
        <p>Add catalog items when the service slice lands.</p>
      </div>
    );
  }

  return (
    <div className="cart-grid">
      <ul className="cart-items" aria-label="Cart items">
        {state.cart.items.map((item) => (
          <li className="cart-item" key={item.id}>
            <div>
              <strong>{item.name}</strong>
              <p>
                Qty {item.quantity} · {formatMoney(item.unitPriceCents, state.cart.currency)}
              </p>
            </div>
            <span>{formatMoney(item.lineTotalCents, state.cart.currency)}</span>
          </li>
        ))}
      </ul>

      <dl className="cart-summary" aria-label="Cart summary">
        <div>
          <dt>Subtotal</dt>
          <dd>{formatMoney(state.cart.subtotalCents, state.cart.currency)}</dd>
        </div>
        <div>
          <dt>Estimated tax</dt>
          <dd>{formatMoney(state.cart.estimatedTaxCents, state.cart.currency)}</dd>
        </div>
        <div className="total-row">
          <dt>Total</dt>
          <dd>{formatMoney(state.cart.totalCents, state.cart.currency)}</dd>
        </div>
      </dl>
    </div>
  );
}

async function fetchCart(signal: AbortSignal): Promise<Cart> {
  const response = await callApi("/api/cart", {
    headers: { Accept: "application/json" },
    signal
  });
  if (signal.aborted) throw new DOMException("Request aborted", "AbortError");
  if (response.status === 404) return emptyCart();
  if (!response.ok) throw new Error(`Cart request failed (${response.status})`);

  const body = (await response.json()) as unknown;
  if (!isCart(body)) throw new Error("Cart response had an unexpected shape");
  return body;
}

function emptyCart(): Cart {
  return {
    id: "current",
    currency: "USD",
    items: [],
    subtotalCents: 0,
    estimatedTaxCents: 0,
    totalCents: 0
  };
}

function isCart(value: unknown): value is Cart {
  if (value === null || typeof value !== "object") return false;
  const cart = value as Record<string, unknown>;
  return (
    typeof cart.id === "string" &&
    typeof cart.currency === "string" &&
    Array.isArray(cart.items) &&
    cart.items.every(isCartItem) &&
    isCurrencyCode(cart.currency) &&
    isNonNegativeInteger(cart.subtotalCents) &&
    isNonNegativeInteger(cart.estimatedTaxCents) &&
    isNonNegativeInteger(cart.totalCents)
  );
}

function isCartItem(value: unknown): value is CartItem {
  if (value === null || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return (
    typeof item.id === "string" &&
    typeof item.name === "string" &&
    isNonNegativeInteger(item.quantity) &&
    isNonNegativeInteger(item.unitPriceCents) &&
    isNonNegativeInteger(item.lineTotalCents)
  );
}

function isNonNegativeInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0;
}

function isCurrencyCode(value: unknown): value is string {
  return typeof value === "string" && /^[A-Z]{3}$/.test(value);
}

function formatMoney(cents: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency
  }).format(cents / 100);
}
