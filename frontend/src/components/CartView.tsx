import { loginHref } from "@/auth";
import { formatMoney, type Cart, type CartItem } from "@/lib/commerce";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";

// Pure presentational cart screen. It receives the already-derived auth flag
// plus the useCart() result projected into (status, cart, error) and renders
// the matching branch — no hooks, no fetch, no token state, so it is testable
// without the router or a QueryClient. The token boundary lives entirely
// upstream: CartRoute reads identity via useMe() and cart data via useCart(),
// and only the plain loginHref() <a> appears here.
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

// The summary is extracted into its own component so a re-render driven by an
// item change (or vice versa) does not churn the other. The compiler memoizes
// it automatically; its props are primitives.
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

// The item list is likewise its own component so summary re-renders leave the
// (potentially long) line-item list untouched — the compiler memoizes it.
function CartItemList({
  items,
  currency
}: {
  readonly items: readonly CartItem[];
  readonly currency: string;
}) {
  return (
    <ul aria-label="Cart items" className="divide-y">
      {items.map((item) => (
        <li key={item.id} className="flex items-baseline justify-between gap-4 py-3">
          <div className="min-w-0">
            <p className="truncate font-medium">{item.name}</p>
            <p className="text-sm text-muted-foreground tabular-nums">
              {item.quantity} · {formatMoney(item.unitPriceCents, currency)}
            </p>
          </div>
          <span className="shrink-0 tabular-nums">{formatMoney(item.lineTotalCents, currency)}</span>
        </li>
      ))}
    </ul>
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
// loading/error/empty/loaded decision stays a single expression.
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
      <CartSummary
        subtotalCents={cart.subtotalCents}
        estimatedTaxCents={cart.estimatedTaxCents}
        totalCents={cart.totalCents}
        currency={cart.currency}
      />
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
