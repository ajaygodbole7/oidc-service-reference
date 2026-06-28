import { useActionState, useOptimistic, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { loginHref } from "@/auth";
import {
  placeOrder,
  removeCartItem,
  formatMoney,
  type Cart,
  type CartItem
} from "@/lib/commerce";
import { cartQueryOptions, catalogQueryOptions } from "@/lib/queries";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";

// Cart screen with the React 19 checkout Action. CartRoute projects the useMe()
// + useCart() results into (authenticated, cart, status, error); rendering and
// the cart-mutation/checkout Actions live here. The token boundary stays
// upstream: only the plain loginHref() <a> and same-origin /api calls (via
// callApi inside the commerce mutations) appear here — no token state.
//
// rendering-conditional-render: every branch is an explicit ternary (? :),
// never `&&`, so a falsy value (e.g. an empty items array, a 0 total) can never
// leak a stray `0`/`""` into the tree.

type CartStatus = "pending" | "error" | "success";

type CartViewProps = {
  readonly authenticated: boolean;
  readonly cart: Cart | undefined;
  readonly status: CartStatus;
  readonly error: unknown;
};

const PAYMENT_METHODS = [
  { id: "pm-card-1", label: "Visa ending 4242" },
  { id: "pm-card-2", label: "Mastercard ending 4444" }
] as const;

function CartSummary({
  subtotalCents,
  estimatedTaxCents,
  totalCents,
  currency
}: {
  readonly subtotalCents: number;
  readonly estimatedTaxCents: number;
  readonly totalCents: number;
  readonly currency: string;
}) {
  return (
    <dl role="group" aria-label="Order summary" className="grid grid-cols-2 gap-y-2 text-sm">
      <dt className="text-muted-foreground">Subtotal</dt>
      <dd className="text-right tabular-nums">{formatMoney(subtotalCents, currency)}</dd>

      <dt className="text-muted-foreground">Estimated tax</dt>
      <dd className="text-right tabular-nums">{formatMoney(estimatedTaxCents, currency)}</dd>

      <dt className="col-span-2">
        <Separator className="my-1" />
      </dt>

      <dt className="font-semibold">Total</dt>
      <dd className="text-right font-semibold tabular-nums">{formatMoney(totalCents, currency)}</dd>
    </dl>
  );
}

// Each line carries a minimal "Remove" Action (DELETE /api/cart/items/{id}),
// rounding out the cart-mutation surface. The Action invalidates the cart query
// so the badge + summary reconcile to the server's truth.
function CartItemList({
  items,
  currency
}: {
  readonly items: readonly CartItem[];
  readonly currency: string;
}) {
  const queryClient = useQueryClient();
  // Resolve each line's display name from the catalog. The cart stores only productId/qty/price, so
  // CartResponse echoes the product id as the line name; the SPA already fetches the catalog, so join
  // item.productId to the catalog product name here, falling back to the id echo.
  const { data: catalogProducts } = useQuery(catalogQueryOptions());
  const nameByProductId = new Map(
    (catalogProducts ?? []).map((product) => [product.id, product.name])
  );
  const [, submitRemove, isRemoving] = useActionState<null, FormData>(
    async (_prev, formData) => {
      const productId = String(formData.get("productId") ?? "");
      if (productId !== "") {
        await removeCartItem(productId);
        await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
      }
      return null;
    },
    null
  );

  return (
    <ul aria-label="Cart items" className="divide-y">
      {items.map((item) => (
        <li key={item.id} className="flex items-baseline justify-between gap-4 py-3">
          <div className="min-w-0">
            <p className="truncate font-medium">{nameByProductId.get(item.productId) ?? item.name}</p>
            <p className="text-sm text-muted-foreground tabular-nums">
              {item.quantity} · {formatMoney(item.unitPriceCents, currency)}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-3">
            <span className="tabular-nums">{formatMoney(item.lineTotalCents, currency)}</span>
            <form action={submitRemove}>
              <input type="hidden" name="productId" value={item.productId} />
              <Button type="submit" variant="ghost" size="xs" disabled={isRemoving}>
                Remove
              </Button>
            </form>
          </div>
        </li>
      ))}
    </ul>
  );
}

// The React 19 checkout Action. It mints a fresh Idempotency-Key per submit
// (crypto.randomUUID), POSTs through placeOrder (callApi → CSRF + 401 handling),
// invalidates the now-consumed cart, then navigates to the order confirmation.
//
// useOptimistic drives the local pending UI: while the order is placing,
// optimisticPlacing flips true so the button reads "Placing order…" and the
// form disables, reverting automatically if the Action throws.
function CheckoutForm({ cart }: { readonly cart: Cart }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [paymentMethodId, setPaymentMethodId] = useState<string>(PAYMENT_METHODS[0].id);

  const [optimisticPlacing, setOptimisticPlacing] = useOptimistic<boolean, boolean>(
    false,
    (_current, next) => next
  );

  const [error, submitCheckout, isPlacing] = useActionState<string | null, FormData>(
    async (_prev, formData) => {
      setOptimisticPlacing(true);
      const shippingPostalCode = String(formData.get("postalCode") ?? "").trim();
      try {
        const order = await placeOrder(
          { paymentMethodId, shippingPostalCode },
          crypto.randomUUID()
        );
        await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
        await navigate({ to: "/orders/$orderId", params: { orderId: order.id } });
        return null;
      } catch (e) {
        return e instanceof Error ? e.message : "Checkout failed";
      }
    },
    null
  );

  const busy = isPlacing || optimisticPlacing;

  return (
    <form action={submitCheckout} className="space-y-4">
      <CartSummary
        subtotalCents={cart.subtotalCents}
        estimatedTaxCents={cart.estimatedTaxCents}
        totalCents={cart.totalCents}
        currency={cart.currency}
      />

      <div className="space-y-2">
        <label htmlFor="postalCode" className="text-sm font-medium">
          Shipping postal code
        </label>
        <input
          id="postalCode"
          name="postalCode"
          required
          autoComplete="postal-code"
          disabled={busy}
          className="flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
        />
      </div>

      <div className="space-y-2">
        <label htmlFor="paymentMethod" className="text-sm font-medium">
          Payment method
        </label>
        <select
          id="paymentMethod"
          name="paymentMethod"
          value={paymentMethodId}
          disabled={busy}
          onChange={(e) => setPaymentMethodId(e.target.value)}
          className="flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
        >
          {PAYMENT_METHODS.map((method) => (
            <option key={method.id} value={method.id}>
              {method.label}
            </option>
          ))}
        </select>
      </div>

      <Button type="submit" className="w-full" disabled={busy}>
        {busy ? "Placing order…" : "Place order"}
      </Button>

      {error !== null ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}

function SignInPanel() {
  return (
    <Card className="mx-auto max-w-md text-center">
      <CardHeader>
        <CardTitle className="text-base">Sign in to view your cart</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-4 text-sm text-muted-foreground">
        <p>Your cart is tied to your account. Sign in to see what you have saved.</p>
        <Button asChild size="sm">
          <a href={loginHref()} data-testid="sign-in-link">
            Sign in
          </a>
        </Button>
      </CardContent>
    </Card>
  );
}

function CartSkeleton() {
  return (
    <div className="space-y-3" aria-hidden="true">
      <Skeleton className="h-6 w-1/2" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

// The authenticated body is its own ternary chain over the cart status so the
// loading/error/empty/loaded decision stays a single expression. The loaded
// branch shows the line items + the checkout form; the empty branch hides
// checkout entirely.
function AuthenticatedCart({ cart, status, error }: Omit<CartViewProps, "authenticated">) {
  return status === "pending" ? (
    <CartSkeleton />
  ) : status === "error" ? (
    <p role="alert" className="text-sm text-destructive">
      We couldn&apos;t load your cart{error instanceof Error ? `: ${error.message}` : ""}. Please try
      again.
    </p>
  ) : cart && cart.items.length > 0 ? (
    <div className="space-y-6">
      <CartItemList items={cart.items} currency={cart.currency} />
      <CheckoutForm cart={cart} />
    </div>
  ) : (
    <p className="text-sm text-muted-foreground">Your cart is empty.</p>
  );
}

export function CartView({ authenticated, cart, status, error }: CartViewProps) {
  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-semibold tracking-tight">Cart</h1>
      {authenticated ? (
        <AuthenticatedCart cart={cart} status={status} error={error} />
      ) : (
        <SignInPanel />
      )}
    </section>
  );
}
