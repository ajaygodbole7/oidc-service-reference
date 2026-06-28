import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { createLazyRoute, Link, useParams } from "@tanstack/react-router";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, type Order } from "@/lib/commerce";
import { catalogQueryOptions, orderQueryOptions } from "@/lib/queries";

// Order confirmation route. The loader (src/router.tsx) prefetches the order
// into the Query cache via ensureQueryData(orderQueryOptions(orderId)); this
// component reads the same cache entry with useSuspenseQuery, so the order is
// guaranteed present. fetchOrder throws on non-OK (incl. 404), so a missing
// order makes the loader/query reject → the route's errorComponent renders the
// not-found state. Fail-closed: a missing order never renders a partial body.
function OrderScreen() {
  const { orderId } = useParams({ from: "/orders/$orderId" });
  const { data: order } = useSuspenseQuery(orderQueryOptions(orderId));
  return <OrderConfirmation order={order} />;
}

function ContinueShopping() {
  return (
    <Button asChild variant="ghost" size="sm">
      <Link to="/">← Continue shopping</Link>
    </Button>
  );
}

function OrderConfirmation({ order }: { readonly order: Order }) {
  const { data: catalogProducts } = useQuery(catalogQueryOptions());
  const nameByProductId = new Map(
    (catalogProducts ?? []).map((product) => [product.id, product.name])
  );
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <ContinueShopping />
      <Card>
        <CardHeader>
          <h1 className="text-2xl font-semibold tracking-tight">Order placed</h1>
          <p className="text-sm text-muted-foreground">
            Thanks — your order is confirmed.
          </p>
        </CardHeader>
        <Separator />
        <CardContent className="space-y-6 pt-6">
          <dl className="grid grid-cols-2 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Order ID</dt>
            <dd className="text-right font-medium tabular-nums">{order.id}</dd>

            <dt className="text-muted-foreground">Status</dt>
            <dd className="text-right font-medium">{order.status}</dd>

            <dt className="text-muted-foreground">Total</dt>
            <dd className="text-right font-medium tabular-nums">
              {formatMoney(order.totalCents, order.currency)}
            </dd>
          </dl>

          <ul aria-label="Order items" className="divide-y">
            {order.lines.map((line) => (
              <li
                key={line.productId}
                className="flex items-baseline justify-between gap-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate font-medium">
                    {nameByProductId.get(line.productId) ?? line.name ?? line.productId}
                  </p>
                  <p className="text-sm text-muted-foreground tabular-nums">
                    {line.quantity} · {formatMoney(line.unitPriceCents, order.currency)}
                  </p>
                </div>
                <span className="shrink-0 tabular-nums">
                  {formatMoney(line.lineTotalCents, order.currency)}
                </span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}

function OrderPending() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <ContinueShopping />
      <Card>
        <CardHeader className="space-y-2">
          <Skeleton className="h-7 w-40" />
          <Skeleton className="h-4 w-56" />
        </CardHeader>
        <Separator />
        <CardContent className="space-y-2 pt-6">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-16 w-full" />
        </CardContent>
      </Card>
    </div>
  );
}

// errorComponent for the route; a 404 (or any fetch failure) lands here as the
// not-found / unavailable state. The raw error message is not surfaced — the
// not-found copy is the intended UX for a missing order.
function OrderError() {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <ContinueShopping />
      <Card role="alert">
        <CardHeader>
          <CardTitle className="text-lg">Order not found</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          This order could not be found or is currently unavailable.
        </CardContent>
      </Card>
    </div>
  );
}

export const Route = createLazyRoute("/orders/$orderId")({
  component: OrderScreen,
  pendingComponent: OrderPending,
  errorComponent: OrderError
});
