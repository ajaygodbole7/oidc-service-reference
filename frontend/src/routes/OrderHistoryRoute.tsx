import { useMemo, useState } from "react";
import { useSuspenseQuery, useQueryClient } from "@tanstack/react-query";
import { createLazyRoute, Link, type ErrorComponentProps } from "@tanstack/react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { formatMoney, type Order, type OrderPage } from "@/lib/commerce";
import { catalogQueryOptions, ordersQueryOptions } from "@/lib/queries";

const orderDateFormatter = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short"
});

function OrderHistoryScreen() {
  const { data: firstPage } = useSuspenseQuery(ordersQueryOptions());
  const { data: catalogProducts } = useSuspenseQuery(catalogQueryOptions());
  const queryClient = useQueryClient();

  const [extraPages, setExtraPages] = useState<readonly OrderPage[]>([]);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);

  const allPages = useMemo(() => [firstPage, ...extraPages], [firstPage, extraPages]);
  const orders = useMemo(() => allPages.flatMap((p) => p.items), [allPages]);
  const nextCursor = allPages.at(-1)?.nextCursor;
  const nameByProductId = useMemo(
    () => new Map(catalogProducts.map((p) => [p.id, p.name])),
    [catalogProducts]
  );

  async function handleLoadMore() {
    if (nextCursor === undefined || isLoadingMore) return;
    setIsLoadingMore(true);
    setLoadMoreError(null);
    try {
      const page = await queryClient.fetchQuery(ordersQueryOptions(nextCursor));
      setExtraPages((prev) => [...prev, page]);
    } catch (err) {
      setLoadMoreError(err instanceof Error ? err.message : "Failed to load more orders.");
    } finally {
      setIsLoadingMore(false);
    }
  }

  return (
    <section aria-labelledby="orders-heading" className="flex flex-col gap-4">
      <header className="flex items-baseline justify-between gap-4">
        <h1 id="orders-heading" className="text-xl font-semibold tracking-tight">
          Orders
        </h1>
        <p className="text-sm text-muted-foreground tabular-nums">
          {`${orders.length} ${orders.length === 1 ? "order" : "orders"}`}
        </p>
      </header>
      <Separator />
      {orders.length === 0 ? (
        <EmptyOrders />
      ) : (
        <OrderList orders={orders} nameByProductId={nameByProductId} />
      )}
      {loadMoreError ? (
        <p className="text-sm text-destructive" role="alert">
          {loadMoreError}
        </p>
      ) : null}
      {nextCursor !== undefined ? (
        <Button
          variant="outline"
          size="sm"
          className="self-start"
          onClick={handleLoadMore}
          disabled={isLoadingMore}
        >
          {isLoadingMore ? "Loading…" : "Load more orders"}
        </Button>
      ) : null}
    </section>
  );
}

function EmptyOrders() {
  return (
    <div className="rounded-lg border border-dashed p-12 text-center">
      <p className="text-base font-medium">No orders yet</p>
      <Button asChild variant="outline" size="sm" className="mt-4">
        <Link to="/">Browse catalog</Link>
      </Button>
    </div>
  );
}

function OrderList({
  orders,
  nameByProductId
}: {
  readonly orders: readonly Order[];
  readonly nameByProductId: ReadonlyMap<string, string>;
}) {
  return (
    <div className="flex flex-col gap-3" aria-label="Order history">
      {orders.map((order) => (
        <Card key={order.id}>
          <CardHeader className="flex flex-row items-start justify-between gap-4">
            <div className="min-w-0">
              <CardTitle className="truncate text-base">{order.id}</CardTitle>
              <p className="mt-1 text-sm text-muted-foreground">
                {orderDateFormatter.format(new Date(order.createdAt))}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-3">
              <Badge variant="secondary">{order.status}</Badge>
              <span className="text-sm font-medium tabular-nums">
                {formatMoney(order.totalCents, order.currency)}
              </span>
            </div>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <ul aria-label={`Lines for ${order.id}`} className="divide-y">
              {order.lines.map((line) => (
                <li key={`${order.id}-${line.productId}`} className="flex justify-between gap-4 py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {nameByProductId.get(line.productId) ?? line.name ?? line.productId}
                    </p>
                    <p className="text-xs text-muted-foreground tabular-nums">
                      {line.quantity} · {formatMoney(line.unitPriceCents, order.currency)}
                    </p>
                  </div>
                  <span className="shrink-0 text-sm tabular-nums">
                    {formatMoney(line.lineTotalCents, order.currency)}
                  </span>
                </li>
              ))}
            </ul>
            <Button asChild variant="outline" size="sm" className="self-start">
              <Link to="/orders/$orderId" params={{ orderId: order.id }}>
                View order
              </Link>
            </Button>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function OrdersPending() {
  return (
    <section aria-labelledby="orders-heading" className="flex flex-col gap-4">
      <h1 id="orders-heading" className="text-xl font-semibold tracking-tight">
        Orders
      </h1>
      <Card>
        <CardContent className="flex flex-col gap-3 pt-6">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-3/4" />
        </CardContent>
      </Card>
    </section>
  );
}

function OrdersError({ error }: ErrorComponentProps) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive"
    >
      <p className="font-medium">We could not load orders.</p>
      <p className="mt-1 text-destructive/80">
        {error instanceof Error ? error.message : "Please try again in a moment."}
      </p>
    </div>
  );
}

export const Route = createLazyRoute("/orders")({
  component: OrderHistoryScreen,
  pendingComponent: OrdersPending,
  errorComponent: OrdersError
});
